"""Composition root: único lugar que conhece as classes concretas."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import aio_pika

from src.application.use_cases.scrape_exam import ScrapeExams
from src.config import Settings
from src.domain.ports.exam_source import ExamSource
from src.infrastructure.messaging.rabbitmq_consumer import RabbitMQConsumer
from src.infrastructure.messaging.rabbitmq_publisher import RabbitMQPublisher
from src.infrastructure.sources.pci_mcp import PciMcpSource


def build_sources(settings: Settings) -> dict[str, ExamSource]:
    # fonte nova = uma entrada nova aqui
    sources: list[ExamSource] = [
        PciMcpSource(settings.pci_mcp_url, timeout_s=settings.pci_mcp_timeout_s),
    ]
    return {s.name: s for s in sources}


@asynccontextmanager
async def build_consumer(settings: Settings) -> AsyncIterator[RabbitMQConsumer]:
    # connect_robust reconecta sozinho (e redeclara a topologia) se o RabbitMQ reiniciar
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    async with connection:
        publisher = await RabbitMQPublisher.create(connection, settings.events_exchange)
        use_case = ScrapeExams(build_sources(settings), publisher)
        yield RabbitMQConsumer(
            connection,
            use_case,
            commands_exchange=settings.commands_exchange,
            queue_name=settings.scrape_queue,
            prefetch=settings.prefetch,
            max_deliveries=settings.max_deliveries,
        )
