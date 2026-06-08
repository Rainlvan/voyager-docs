from __future__ import annotations

from dataclasses import dataclass
from typing import Any
import uuid

import psycopg
from psycopg.rows import dict_row


@dataclass
class Job:
    id: str
    document_id: str
    title: str
    original_filename: str
    object_key: str
    content_type: str | None


class Database:
    def __init__(self, dsn: str) -> None:
        self.dsn = dsn

    def connect(self):
        return psycopg.connect(self.dsn, row_factory=dict_row)

    def maintenance_enabled(self) -> bool:
        with self.connect() as conn:
            row = conn.execute("select enabled from system_maintenance where id = 1").fetchone()
            return bool(row and row["enabled"])

    def claim_job(self, worker_id: str) -> Job | None:
        with self.connect() as conn:
            with conn.transaction():
                maintenance = conn.execute("select enabled from system_maintenance where id = 1").fetchone()
                if maintenance and maintenance["enabled"]:
                    return None
                row = conn.execute(
                    """
                    select j.id, d.id as document_id, d.title, d.original_filename, d.object_key, d.content_type
                    from ingestion_jobs j
                    join documents d on d.id = j.document_id
                    where j.status = 'PENDING' and d.deleted_at is null and d.recycled_at is null
                    order by j.created_at asc
                    for update skip locked
                    limit 1
                    """
                ).fetchone()
                if not row:
                    return None
                conn.execute(
                    """
                    update ingestion_jobs
                    set status = 'PROCESSING', attempt_count = attempt_count + 1, locked_by = %s, locked_at = now(), updated_at = now()
                    where id = %s
                    """,
                    (worker_id, row["id"]),
                )
                conn.execute(
                    """
                    update documents
                    set status = 'PROCESSING', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (row["document_id"],),
                )
                data = dict(row)
                data["id"] = str(data["id"])
                data["document_id"] = str(data["document_id"])
                return Job(**data)

    def load_ai_settings(self) -> dict[str, Any]:
        with self.connect() as conn:
            return conn.execute("select * from ai_settings order by id asc limit 1").fetchone()

    def heartbeat(self, worker_id: str, status: str, current_job_id: str | None, message: str | None = None) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                insert into worker_heartbeats(worker_id, status, current_job_id, message, started_at, last_seen_at)
                values (%s, %s, %s, %s, now(), now())
                on conflict(worker_id) do update set
                    status = excluded.status,
                    current_job_id = excluded.current_job_id,
                    message = excluded.message,
                    last_seen_at = now()
                """,
                (worker_id, status, current_job_id, (message or "")[:4000]),
            )

    def document_is_live(self, document_id: str) -> bool:
        with self.connect() as conn:
            row = conn.execute(
                """
                select 1
                from documents
                where id = %s and deleted_at is null and recycled_at is null
                """,
                (document_id,),
            ).fetchone()
            return row is not None

    def replace_index_rows(self, job: Job, chunks: list[dict[str, Any]], visuals: list[dict[str, Any]]) -> bool:
        with self.connect() as conn:
            with conn.transaction():
                row = conn.execute(
                    """
                    select 1
                    from documents
                    where id = %s and deleted_at is null and recycled_at is null
                    for update
                    """,
                    (job.document_id,),
                ).fetchone()
                if not row:
                    return False
                conn.execute("delete from document_chunks where document_id = %s", (job.document_id,))
                conn.execute("delete from visual_units where document_id = %s", (job.document_id,))
                for chunk in chunks:
                    conn.execute(
                        """
                        insert into document_chunks(id, document_id, chunk_index, page_number, content, token_estimate, opensearch_id)
                        values (%s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            chunk["id"],
                            job.document_id,
                            chunk["chunk_index"],
                            chunk["page_number"],
                            chunk["content"],
                            chunk["token_estimate"],
                            chunk["opensearch_id"],
                        ),
                    )
                for visual in visuals:
                    conn.execute(
                        """
                        insert into visual_units(id, document_id, page_number, kind, object_key, caption, ocr_text, opensearch_id)
                        values (%s, %s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            visual["id"],
                            job.document_id,
                            visual["page_number"],
                            visual["kind"],
                            visual["object_key"],
                            visual["caption"],
                            visual["ocr_text"],
                            visual["opensearch_id"],
                        ),
                    )
                return True

    def mark_succeeded(self, job: Job) -> bool:
        with self.connect() as conn:
            with conn.transaction():
                conn.execute("update ingestion_jobs set status = 'SUCCEEDED', updated_at = now() where id = %s", (job.id,))
                result = conn.execute(
                    """
                    update documents
                    set status = 'READY', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (job.document_id,),
                )
                return result.rowcount > 0

    def mark_failed(self, job: Job, error: str) -> None:
        with self.connect() as conn:
            with conn.transaction():
                conn.execute(
                    "update ingestion_jobs set status = 'FAILED', error_message = %s, updated_at = now() where id = %s",
                    (error[:4000], job.id),
                )
                conn.execute(
                    """
                    update documents
                    set status = 'FAILED', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (job.document_id,),
                )

    def mark_batch_submitted(
        self,
        job: Job,
        provider_batch_id: str,
        input_file_id: str | None,
        request_object_key: str | None,
        status: str,
    ) -> None:
        with self.connect() as conn:
            with conn.transaction():
                conn.execute(
                    """
                    insert into embedding_batches(
                        id, ingestion_job_id, document_id, provider_batch_id, input_file_id, status, request_object_key
                    )
                    values (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (str(uuid.uuid4()), job.id, job.document_id, provider_batch_id, input_file_id, status, request_object_key),
                )
                conn.execute(
                    """
                    update ingestion_jobs
                    set status = 'BATCH_SUBMITTED', locked_at = null, locked_by = null, updated_at = now()
                    where id = %s
                    """,
                    (job.id,),
                )
                conn.execute(
                    """
                    update documents
                    set status = 'BATCHING', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (job.document_id,),
                )

    def pending_embedding_batches(self, limit: int = 5) -> list[dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute(
                """
                select
                    eb.id,
                    eb.ingestion_job_id,
                    eb.document_id,
                    eb.provider_batch_id,
                    eb.status,
                    d.title,
                    d.original_filename
                from embedding_batches eb
                join documents d on d.id = eb.document_id
                where eb.status not in ('completed', 'failed', 'expired', 'cancelled')
                order by eb.created_at asc
                limit %s
                """,
                (limit,),
            ).fetchall()
            return [dict(row) for row in rows]

    def update_embedding_batch_status(
        self,
        batch_id: str,
        status: str,
        output_file_id: str | None = None,
        error_file_id: str | None = None,
        error_message: str | None = None,
    ) -> None:
        with self.connect() as conn:
            conn.execute(
                """
                update embedding_batches
                set status = %s,
                    output_file_id = coalesce(%s, output_file_id),
                    error_file_id = coalesce(%s, error_file_id),
                    error_message = %s,
                    updated_at = now()
                where id = %s
                """,
                (status, output_file_id, error_file_id, error_message, batch_id),
            )

    def load_chunks(self, document_id: str) -> list[dict[str, Any]]:
        with self.connect() as conn:
            rows = conn.execute(
                """
                select id, chunk_index, page_number, content, token_estimate, opensearch_id
                from document_chunks
                where document_id = %s
                order by chunk_index asc
                """,
                (document_id,),
            ).fetchall()
            return [dict(row) for row in rows]

    def mark_batch_completed(self, batch_id: str, document_id: str, job_id: str) -> bool:
        with self.connect() as conn:
            with conn.transaction():
                conn.execute(
                    "update embedding_batches set status = 'completed', updated_at = now() where id = %s",
                    (batch_id,),
                )
                conn.execute("update ingestion_jobs set status = 'SUCCEEDED', updated_at = now() where id = %s", (job_id,))
                result = conn.execute(
                    """
                    update documents
                    set status = 'READY', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (document_id,),
                )
                return result.rowcount > 0

    def mark_batch_failed(self, batch_id: str, document_id: str, job_id: str, status: str, error: str) -> None:
        with self.connect() as conn:
            with conn.transaction():
                conn.execute(
                    """
                    update embedding_batches
                    set status = %s, error_message = %s, updated_at = now()
                    where id = %s
                    """,
                    (status, error[:4000], batch_id),
                )
                conn.execute(
                    """
                    update ingestion_jobs
                    set status = 'FAILED', error_message = %s, updated_at = now()
                    where id = %s
                    """,
                    (error[:4000], job_id),
                )
                conn.execute(
                    """
                    update documents
                    set status = 'FAILED', updated_at = now()
                    where id = %s and deleted_at is null and recycled_at is null
                    """,
                    (document_id,),
                )
