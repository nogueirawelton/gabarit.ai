import os

from src.application.use_cases.ingestion_use_case import IngestionUseCase
from src.infrastructure.adapters.services.scraper import ScraperAdapter
from src.infrastructure.adapters.storage.min_io_storage import MinIOStorageAdapter


class IngestionUseCaseFactory:

    @staticmethod
    def create() -> IngestionUseCase:
        scraper = ScraperAdapter()
        storage = MinIOStorageAdapter(
            endpoint=os.getenv("MINIO_URL"),
            access_key=os.getenv("MINIO_USER"),
            secret_key=os.getenv("MINIO_PASSWORD")
        )

        return IngestionUseCase(scraper)
