from __future__ import annotations

import base64
import hashlib
import math
from pathlib import Path
from typing import Iterable

import requests


class BailianClient:
    def __init__(
        self,
        api_key: str | None,
        text_model: str,
        text_dimension: int,
        multimodal_model: str,
        multimodal_dimension: int,
        region: str,
    ) -> None:
        self.api_key = api_key
        self.text_model = text_model
        self.text_dimension = text_dimension
        self.multimodal_model = multimodal_model
        self.multimodal_dimension = multimodal_dimension
        self.base_url = "https://dashscope.aliyuncs.com/api/v1" if region == "cn-beijing" else "https://dashscope-intl.aliyuncs.com/api/v1"
        self.compatible_base_url = (
            "https://dashscope.aliyuncs.com/compatible-mode/v1"
            if region == "cn-beijing"
            else "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
        )

    def text_embeddings(self, texts: list[str], text_type: str = "document") -> list[list[float]]:
        if not texts:
            return []
        if not self.api_key:
            return [deterministic_vector(text, self.text_dimension) for text in texts]

        response = requests.post(
            f"{self.base_url}/services/embeddings/text-embedding/text-embedding",
            headers=self._headers(),
            json={
                "model": self.text_model,
                "input": {"texts": texts},
                "parameters": {
                    "dimension": self.text_dimension,
                    "text_type": text_type,
                },
            },
            timeout=60,
        )
        response.raise_for_status()
        data = response.json()
        return [item["embedding"] for item in data["output"]["embeddings"]]

    def multimodal_embedding(self, text: str | None, image_path: Path | None) -> list[float]:
        seed = (text or "") + (str(image_path) if image_path else "")
        if not self.api_key:
            return deterministic_vector(seed, self.multimodal_dimension)

        contents: list[dict[str, str]] = []
        if text:
            contents.append({"text": text[:12000]})
        if image_path:
            contents.append({"image": image_data_uri(image_path)})
        response = requests.post(
            f"{self.base_url}/services/embeddings/multimodal-embedding/multimodal-embedding",
            headers=self._headers(),
            json={
                "model": self.multimodal_model,
                "input": {"contents": contents},
                "parameters": {
                    "dimension": self.multimodal_dimension,
                    "enable_fusion": True,
                },
            },
            timeout=90,
        )
        response.raise_for_status()
        data = response.json()
        return data["output"]["embeddings"][0]["embedding"]

    def submit_embedding_batch(self, jsonl_path: Path) -> dict:
        if not self.api_key:
            raise RuntimeError("Batch mode requires a configured Bailian API key")
        with jsonl_path.open("rb") as handle:
            file_response = requests.post(
                f"{self.compatible_base_url}/files",
                headers={"Authorization": f"Bearer {self.api_key}"},
                files={"file": (jsonl_path.name, handle, "application/jsonl")},
                data={"purpose": "batch"},
                timeout=120,
            )
        file_response.raise_for_status()
        file_data = file_response.json()
        input_file_id = file_data["id"]
        batch_response = requests.post(
            f"{self.compatible_base_url}/batches",
            headers=self._headers(),
            json={
                "input_file_id": input_file_id,
                "endpoint": "/v1/embeddings",
                "completion_window": "24h",
            },
            timeout=60,
        )
        batch_response.raise_for_status()
        batch_data = batch_response.json()
        return {
            "input_file_id": input_file_id,
            "batch_id": batch_data["id"],
            "status": batch_data.get("status", "validating"),
        }

    def retrieve_batch(self, batch_id: str) -> dict:
        if not self.api_key:
            raise RuntimeError("Batch polling requires a configured Bailian API key")
        response = requests.get(
            f"{self.compatible_base_url}/batches/{batch_id}",
            headers=self._headers(),
            timeout=60,
        )
        response.raise_for_status()
        return response.json()

    def download_file_text(self, file_id: str) -> str:
        if not self.api_key:
            raise RuntimeError("Batch file download requires a configured Bailian API key")
        response = requests.get(
            f"{self.compatible_base_url}/files/{file_id}/content",
            headers={"Authorization": f"Bearer {self.api_key}"},
            timeout=120,
        )
        response.raise_for_status()
        return response.text

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }


def image_data_uri(path: Path) -> str:
    suffix = path.suffix.lower().lstrip(".") or "png"
    if suffix == "jpg":
        suffix = "jpeg"
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:image/{suffix};base64,{data}"


def deterministic_vector(seed: str, dimension: int) -> list[float]:
    values: list[float] = []
    counter = 0
    while len(values) < dimension:
        digest = hashlib.sha256(f"{seed}:{counter}".encode("utf-8")).digest()
        values.extend(((byte / 255.0) * 2.0 - 1.0) for byte in digest)
        counter += 1
    vector = values[:dimension]
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]
