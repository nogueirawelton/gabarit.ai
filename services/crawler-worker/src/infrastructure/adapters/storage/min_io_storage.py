import hashlib
from typing import io

import httpx

from src.domain.ports.storage import StoragePort


class MinIOStorageAdapter(StoragePort):
    def __init__(self, endpoint, access_key, secret_key):
        self.client = None
        self.bucket = "documents"

    async def download_file(self, client: httpx.AsyncClient, url: str) -> bytes:
        response = await client.get(url, timeout=30.0)
        response.raise_for_status()

        return response.content

    async def hash_file(self, content: bytes) -> str:
        return hashlib.sha256(content).hexdigest()

    async def upload(self, content: bytes, destination: str) -> str:
        data_stream = io.BytesIO(content)

        await self.client.put_object(
            self.bucket,destination,
            data_stream,
            length=len(content),
            content_type="application/pdf"
        )

        return f"{self.bucket}/{destination}"