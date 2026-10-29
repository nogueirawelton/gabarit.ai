"""Leitura dos PDFs no MinIO. O cliente oficial é síncrono: cada chamada roda numa thread."""

import asyncio
import contextlib

from minio import Minio
from minio.error import S3Error
from pydantic import AnyHttpUrl
from urllib3.exceptions import HTTPError

from src.domain.errors import DocumentNotFoundError, TransientError


class MinioDocumentStore:
    def __init__(self, url: AnyHttpUrl, access_key: str, secret_key: str, bucket: str) -> None:
        self._client = Minio(
            f"{url.host}:{url.port}",
            access_key=access_key,
            secret_key=secret_key,
            secure=url.scheme == "https",
            # região fixa: sem ela o cliente pergunta ao MinIO (GetBucketLocation), e a policy do
            # usuário `extraction` só libera GetObject
            region="us-east-1",
        )
        self._bucket = bucket

    async def read(self, key: str) -> bytes:
        try:
            return await asyncio.to_thread(self._read, key)
        except S3Error as e:
            if e.code == "NoSuchKey":
                raise DocumentNotFoundError(f"documento não encontrado: {key}") from e
            # AccessDenied etc.: credencial ou policy errada (infra/minio), não o documento
            raise TransientError(f"MinIO respondeu {e.code} ao ler {key}") from e
        except HTTPError as e:
            raise TransientError(f"MinIO inacessível: {e}") from e

    def _read(self, key: str) -> bytes:
        response = self._client.get_object(self._bucket, key)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    async def check(self) -> None:
        """Confere no boot que o MinIO responde e aceita as credenciais (senão, o boot falha)."""
        with contextlib.suppress(DocumentNotFoundError):
            await self.read("blobs/.probe")
