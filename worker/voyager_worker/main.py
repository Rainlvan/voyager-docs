from __future__ import annotations

import json
import logging
import time
import uuid
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any

from minio import Minio

from voyager_worker.bailian import BailianClient
from voyager_worker.config import settings
from voyager_worker.crypto import decrypt_api_key
from voyager_worker.db import Database, Job
from voyager_worker.extractors import extract_document
from voyager_worker.opensearch import OpenSearchIndexer

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("voyager-worker")

TERMINAL_BATCH_STATUSES = {"completed", "failed", "expired", "cancelled"}


def main() -> None:
    db = Database(settings.postgres_dsn)
    minio = Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )
    log.info("Worker %s started", settings.worker_id)
    db.heartbeat(settings.worker_id, "IDLE", None, "Worker started")
    while True:
        try:
            if db.maintenance_enabled():
                db.heartbeat(settings.worker_id, "MAINTENANCE", None, "System maintenance in progress")
                time.sleep(settings.poll_seconds)
                continue
            poll_embedding_batches(db)
        except Exception:
            log.exception("Batch polling failed")

        job = db.claim_job(settings.worker_id)
        if not job:
            db.heartbeat(settings.worker_id, "IDLE", None, "Waiting for jobs")
            time.sleep(settings.poll_seconds)
            continue
        db.heartbeat(settings.worker_id, "PROCESSING", job.id, f"Processing {job.title}")
        try:
            result = process_job(db, minio, job)
            if result == "batch_submitted":
                db.heartbeat(settings.worker_id, "IDLE", None, f"Batch submitted: {job.title}")
                log.info("Job %s submitted to embedding batch", job.id)
            elif db.mark_succeeded(job):
                db.heartbeat(settings.worker_id, "IDLE", None, f"Last succeeded: {job.title}")
                log.info("Job %s succeeded", job.id)
            else:
                db.heartbeat(settings.worker_id, "IDLE", None, f"Skipped inactive document: {job.title}")
                log.info("Job %s skipped because document is inactive", job.id)
        except Exception as exc:
            log.exception("Job %s failed", job.id)
            db.mark_failed(job, str(exc))
            db.heartbeat(settings.worker_id, "IDLE", None, f"Last failed: {str(exc)[:300]}")


def process_job(db: Database, minio: Minio, job: Job) -> str:
    ai = db.load_ai_settings()
    client = client_from_settings(ai)
    indexer = indexer_from_settings(ai)
    indexer.ensure_index()
    indexer.delete_document(job.document_id)

    with TemporaryDirectory(prefix="voyager-source-") as tmp:
        source_path = Path(tmp) / job.original_filename
        minio.fget_object(settings.minio_bucket, job.object_key, str(source_path))
        extracted = extract_document(source_path, job.original_filename, settings.max_pdf_pages, settings.render_pdf_pages)
        try:
            mode = (ai.get("embedding_invocation_mode") or "REALTIME").upper()
            if mode == "BATCH" and extracted.chunks:
                visual_rows = index_visual_units(client, indexer, minio, job, extracted.visuals)
                text_rows = prepare_text_chunk_rows(job, extracted.chunks)
                if not db.replace_index_rows(job, text_rows, visual_rows):
                    indexer.delete_document(job.document_id)
                    return "skipped"
                batch_path = Path(tmp) / f"embedding-batch-{job.document_id}.jsonl"
                write_embedding_batch_jsonl(client, batch_path, extracted.chunks)
                request_object_key = f"batch-requests/{job.document_id}/{uuid.uuid4()}.jsonl"
                minio.fput_object(settings.minio_bucket, request_object_key, str(batch_path))
                submission = client.submit_embedding_batch(batch_path)
                db.mark_batch_submitted(
                    job,
                    submission["batch_id"],
                    submission.get("input_file_id"),
                    request_object_key,
                    submission.get("status", "validating"),
                )
                return "batch_submitted"

            text_rows = index_text_chunks(client, indexer, job, extracted.chunks)
            visual_rows = index_visual_units(client, indexer, minio, job, extracted.visuals)
            if not db.replace_index_rows(job, text_rows, visual_rows):
                indexer.delete_document(job.document_id)
                return "skipped"
            if not db.document_is_live(job.document_id):
                indexer.delete_document(job.document_id)
                return "skipped"
            return "succeeded"
        finally:
            extracted.temp_dir.cleanup()


def poll_embedding_batches(db: Database) -> None:
    batches = db.pending_embedding_batches()
    if not batches:
        return
    ai = db.load_ai_settings()
    client = client_from_settings(ai)
    indexer = indexer_from_settings(ai)
    indexer.ensure_index()

    for batch in batches:
        batch_id = str(batch["id"])
        document_id = str(batch["document_id"])
        job_id = str(batch["ingestion_job_id"])
        provider_batch_id = batch["provider_batch_id"]
        if not db.document_is_live(document_id):
            indexer.delete_document(document_id)
            db.update_embedding_batch_status(batch_id, "cancelled", error_message="Document is no longer active")
            continue
        remote = client.retrieve_batch(provider_batch_id)
        status = remote.get("status", "unknown")
        output_file_id = remote.get("output_file_id")
        error_file_id = remote.get("error_file_id")
        db.update_embedding_batch_status(batch_id, status, output_file_id, error_file_id)
        if status == "completed":
            if not output_file_id:
                db.mark_batch_failed(batch_id, document_id, job_id, "failed", "Batch completed without output_file_id")
                continue
            output_text = client.download_file_text(output_file_id)
            vectors = parse_embedding_batch_output(output_text)
            chunks = db.load_chunks(document_id)
            index_batch_text_vectors(indexer, batch, chunks, vectors)
            if not db.mark_batch_completed(batch_id, document_id, job_id):
                indexer.delete_document(document_id)
        elif status in TERMINAL_BATCH_STATUSES:
            error_message = ""
            if error_file_id:
                try:
                    error_message = client.download_file_text(error_file_id)[:4000]
                except Exception:
                    error_message = f"Batch status: {status}"
            db.mark_batch_failed(batch_id, document_id, job_id, status, error_message or f"Batch status: {status}")


def client_from_settings(ai: dict[str, Any]) -> BailianClient:
    api_key = decrypt_api_key(ai.get("api_key_ciphertext"), settings.crypto_secret)
    return BailianClient(
        api_key=api_key,
        text_model=ai["text_embedding_model"],
        text_dimension=ai["text_embedding_dimension"],
        multimodal_model=ai["multimodal_embedding_model"],
        multimodal_dimension=ai["multimodal_embedding_dimension"],
        region=ai["region"],
    )


def indexer_from_settings(ai: dict[str, Any]) -> OpenSearchIndexer:
    return OpenSearchIndexer(
        settings.opensearch_endpoint,
        settings.opensearch_index,
        ai["text_embedding_dimension"],
        ai["multimodal_embedding_dimension"],
    )


def index_text_chunks(client: BailianClient, indexer: OpenSearchIndexer, job: Job, chunks) -> list[dict]:
    rows: list[dict] = []
    batch_size = 8
    for start in range(0, len(chunks), batch_size):
        batch = chunks[start:start + batch_size]
        vectors = client.text_embeddings([chunk.content for chunk in batch], text_type="document")
        for offset, (chunk, vector) in enumerate(zip(batch, vectors)):
            chunk_index = start + offset
            chunk_id = str(uuid.uuid4())
            opensearch_id = f"{job.document_id}:chunk:{chunk_index}"
            body = {
                "document_id": job.document_id,
                "chunk_id": chunk_id,
                "title": job.title,
                "original_filename": job.original_filename,
                "status": "READY",
                "kind": "text",
                "content": chunk.content,
                "page_number": chunk.page_number,
                "content_vector": vector,
            }
            indexer.index_document(opensearch_id, body)
            rows.append(
                {
                    "id": chunk_id,
                    "chunk_index": chunk_index,
                    "page_number": chunk.page_number,
                    "content": chunk.content,
                    "token_estimate": max(1, len(chunk.content) // 2),
                    "opensearch_id": opensearch_id,
                }
            )
    return rows


def prepare_text_chunk_rows(job: Job, chunks) -> list[dict]:
    rows: list[dict] = []
    for chunk_index, chunk in enumerate(chunks):
        rows.append(
            {
                "id": str(uuid.uuid4()),
                "chunk_index": chunk_index,
                "page_number": chunk.page_number,
                "content": chunk.content,
                "token_estimate": max(1, len(chunk.content) // 2),
                "opensearch_id": f"{job.document_id}:chunk:{chunk_index}",
            }
        )
    return rows


def write_embedding_batch_jsonl(client: BailianClient, path: Path, chunks) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for chunk_index, chunk in enumerate(chunks):
            line = {
                "custom_id": f"chunk:{chunk_index}",
                "method": "POST",
                "url": "/v1/embeddings",
                "body": {
                    "model": client.text_model,
                    "input": chunk.content,
                    "dimensions": client.text_dimension,
                    "encoding_format": "float",
                },
            }
            handle.write(json.dumps(line, ensure_ascii=False) + "\n")


def parse_embedding_batch_output(text: str) -> dict[int, list[float]]:
    vectors: dict[int, list[float]] = {}
    for line in text.splitlines():
        if not line.strip():
            continue
        payload = json.loads(line)
        custom_id = payload.get("custom_id", "")
        if not custom_id.startswith("chunk:"):
            continue
        chunk_index = int(custom_id.split(":", 1)[1])
        body = payload.get("response", {}).get("body", {})
        data = body.get("data") or body.get("output", {}).get("embeddings") or []
        if data and "embedding" in data[0]:
            vectors[chunk_index] = data[0]["embedding"]
    return vectors


def index_batch_text_vectors(indexer: OpenSearchIndexer, batch: dict[str, Any], chunks: list[dict], vectors: dict[int, list[float]]) -> None:
    chunk_by_index = {chunk["chunk_index"]: chunk for chunk in chunks}
    for chunk_index, vector in vectors.items():
        chunk = chunk_by_index.get(chunk_index)
        if not chunk:
            continue
        body = {
            "document_id": str(batch["document_id"]),
            "chunk_id": str(chunk["id"]),
            "title": batch["title"],
            "original_filename": batch["original_filename"],
            "status": "READY",
            "kind": "text",
            "content": chunk["content"],
            "page_number": chunk["page_number"],
            "content_vector": vector,
        }
        indexer.index_document(chunk["opensearch_id"], body)


def index_visual_units(client: BailianClient, indexer: OpenSearchIndexer, minio: Minio, job: Job, visuals) -> list[dict]:
    rows: list[dict] = []
    for index, visual in enumerate(visuals):
        visual_id = str(uuid.uuid4())
        object_key = f"derived/{job.document_id}/visual-{index}{visual.path.suffix.lower() or '.png'}"
        minio.fput_object(settings.minio_bucket, object_key, str(visual.path))
        vector = client.multimodal_embedding(visual.ocr_text or job.title, visual.path)
        opensearch_id = f"{job.document_id}:visual:{index}"
        body = {
            "document_id": job.document_id,
            "visual_id": visual_id,
            "title": job.title,
            "original_filename": job.original_filename,
            "status": "READY",
            "kind": visual.kind,
            "ocr_text": visual.ocr_text,
            "caption": visual.ocr_text[:300] if visual.ocr_text else job.title,
            "page_number": visual.page_number,
            "visual_vector": vector,
        }
        indexer.index_document(opensearch_id, body)
        rows.append(
            {
                "id": visual_id,
                "page_number": visual.page_number,
                "kind": visual.kind,
                "object_key": object_key,
                "caption": body["caption"],
                "ocr_text": visual.ocr_text,
                "opensearch_id": opensearch_id,
            }
        )
    return rows


if __name__ == "__main__":
    main()
