from typing import Protocol, List

import httpx

from src.domain.entities.exam import Exam


class StoragePort(Protocol):
    async def download_file(self, client: httpx.AsyncClient, url: str)-> bytes:
        ...

    async def hash_file(self, content: bytes)-> str:
        ...

    async def upload_file(self, content: bytes, file: bytes)-> str:
        ...