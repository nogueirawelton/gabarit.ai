import logging
from collections.abc import Mapping

from src.application.messages import ExamDiscovered, PositionCatalogDiscovered, ScrapeRequested
from src.domain.errors import UnknownSourceError
from src.domain.ports.event_publisher import EventPublisher
from src.domain.ports.exam_source import ExamSource

logger = logging.getLogger(__name__)


class ScrapeExams:
    """Coleta tudo de uma fonte e publica o catálogo de cargos e um exam.discovered por concurso."""

    def __init__(self, sources: Mapping[str, ExamSource], publisher: EventPublisher) -> None:
        self._sources = sources
        self._publisher = publisher

    async def execute(self, command: ScrapeRequested, correlation_id: str | None = None) -> int:
        source = self._sources.get(command.source)
        if source is None:
            raise UnknownSourceError(f"fonte desconhecida: {command.source!r}")

        # Busca tudo antes de publicar: se a fonte falha no meio, nada sai
        collection = await source.collect()
        logger.info(
            "coleta concluída",
            extra={
                "source": source.name,
                "count": len(collection.exams),
                "catalog": len(collection.catalog),
            },
        )

        if collection.catalog:
            catalog = PositionCatalogDiscovered.from_catalog(
                source.name, collection.catalog, correlation_id=correlation_id
            )
            await self._publisher.publish(
                event_id=catalog.event_id,
                event_type=catalog.event_type,
                body=catalog.model_dump_json().encode(),
            )

        for exam in collection.exams:
            event = ExamDiscovered.from_exam(exam, correlation_id=correlation_id)
            await self._publisher.publish(
                event_id=event.event_id,
                event_type=event.event_type,
                body=event.model_dump_json().encode(),
            )

        logger.info(
            "eventos publicados", extra={"source": source.name, "count": len(collection.exams)}
        )
        return len(collection.exams)
