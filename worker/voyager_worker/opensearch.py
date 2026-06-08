from __future__ import annotations

from typing import Any

import requests


class OpenSearchIndexer:
    def __init__(self, endpoint: str, index: str, text_dimension: int, visual_dimension: int) -> None:
        self.endpoint = endpoint.rstrip("/")
        self.index_name = index
        self.text_dimension = text_dimension
        self.visual_dimension = visual_dimension

    def ensure_index(self) -> None:
        response = requests.head(f"{self.endpoint}/{self.index_name}", timeout=15)
        if response.status_code == 200:
            return
        mapping = {
            "settings": {"index": {"knn": True}},
            "mappings": {
                "properties": {
                    "document_id": {"type": "keyword"},
                    "chunk_id": {"type": "keyword"},
                    "visual_id": {"type": "keyword"},
                    "title": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
                    "original_filename": {"type": "keyword"},
                    "status": {"type": "keyword"},
                    "kind": {"type": "keyword"},
                    "content": {"type": "text", "analyzer": "standard"},
                    "ocr_text": {"type": "text", "analyzer": "standard"},
                    "caption": {"type": "text", "analyzer": "standard"},
                    "page_number": {"type": "integer"},
                    "content_vector": {
                        "type": "knn_vector",
                        "dimension": self.text_dimension,
                        "method": {"name": "hnsw", "engine": "lucene", "space_type": "cosinesimil"},
                    },
                    "visual_vector": {
                        "type": "knn_vector",
                        "dimension": self.visual_dimension,
                        "method": {"name": "hnsw", "engine": "lucene", "space_type": "cosinesimil"},
                    },
                }
            },
        }
        create = requests.put(f"{self.endpoint}/{self.index_name}", json=mapping, timeout=30)
        if create.status_code not in {200, 201}:
            create.raise_for_status()

    def delete_document(self, document_id: str) -> None:
        requests.post(
            f"{self.endpoint}/{self.index_name}/_delete_by_query",
            json={"query": {"term": {"document_id": document_id}}},
            timeout=30,
        )

    def index_document(self, doc_id: str, body: dict[str, Any]) -> None:
        response = requests.put(f"{self.endpoint}/{self.index_name}/_doc/{doc_id}", json=body, timeout=30)
        response.raise_for_status()
