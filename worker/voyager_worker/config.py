from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    postgres_dsn: str = os.getenv(
        "VOYAGER_POSTGRES_DSN",
        "postgresql://voyager:voyager_pass@127.0.0.1:5432/voyager",
    )
    minio_endpoint: str = os.getenv("MINIO_ENDPOINT", "127.0.0.1:9000").replace("http://", "").replace("https://", "")
    minio_secure: bool = os.getenv("MINIO_SECURE", "false").lower() == "true"
    minio_access_key: str = os.getenv("MINIO_ACCESS_KEY", "voyager_minio")
    minio_secret_key: str = os.getenv("MINIO_SECRET_KEY", "voyager_minio_pass")
    minio_bucket: str = os.getenv("MINIO_BUCKET", "voyager-documents")
    opensearch_endpoint: str = os.getenv("OPENSEARCH_ENDPOINT", "http://127.0.0.1:19200").rstrip("/")
    opensearch_index: str = os.getenv("OPENSEARCH_DOCUMENT_INDEX", "voyager-documents")
    crypto_secret: str = os.getenv("VOYAGER_CRYPTO_SECRET", "local-dev-crypto-secret-change-me")
    worker_id: str = os.getenv("VOYAGER_WORKER_ID", "local-worker")
    poll_seconds: float = float(os.getenv("VOYAGER_WORKER_POLL_SECONDS", "3"))
    max_pdf_pages: int = int(os.getenv("VOYAGER_MAX_PDF_PAGES", "80"))
    render_pdf_pages: int = int(os.getenv("VOYAGER_RENDER_PDF_PAGES", "30"))


settings = Settings()
