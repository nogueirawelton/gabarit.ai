"""Composition root: único lugar que conhece as classes concretas."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import aio_pika
import anthropic

from src.application.tasks import SYSTEM_PROMPT
from src.application.use_cases.process_extraction import ProcessExtraction
from src.config import Settings
from src.infrastructure.llm.claude import ClaudeClient
from src.infrastructure.messaging.rabbitmq_consumer import RabbitMQConsumer
from src.infrastructure.messaging.rabbitmq_publisher import RabbitMQPublisher
from src.infrastructure.pdf.pypdf_pages import PypdfPages
from src.infrastructure.storage.minio_store import MinioDocumentStore


@asynccontextmanager
async def build_consumer(settings: Settings) -> AsyncIterator[RabbitMQConsumer]:
    store = MinioDocumentStore(
        settings.minio_url,
        settings.minio_access_key,
        settings.minio_secret_key.get_secret_value(),
        settings.documents_bucket,
    )
    await store.check()

    # connect_robust reconecta sozinho se o RabbitMQ reiniciar
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    claude = anthropic.AsyncAnthropic(api_key=settings.anthropic_api_key.get_secret_value())
    async with connection, claude:
        llm = ClaudeClient(
            claude,
            model=settings.claude_model,
            system_prompt=SYSTEM_PROMPT,
            file_ttl_s=settings.files_ttl_s,
        )
        publisher = await RabbitMQPublisher.create(connection, settings.events_exchange)
        yield RabbitMQConsumer(
            connection,
            ProcessExtraction(store, llm, publisher, PypdfPages()),
            queue_name=settings.requests_queue,
            prefetch=settings.prefetch,
            max_deliveries=settings.max_deliveries,
        )
