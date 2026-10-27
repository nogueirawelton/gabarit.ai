import logging
from collections.abc import Mapping

from src.application.messages import ExamDiscovered, ScrapeRequested
from src.domain.errors import UnknownSourceError
from src.domain.ports.event_publisher import EventPublisher
from src.domain.ports.exam_source import ExamSource

logger = logging.getLogger(__name__)


class ScrapeExams:
    """Coleta todos os concursos de uma fonte e publica um exam.discovered por concurso."""

    def __init__(self, sources: Mapping[str, ExamSource], publisher: EventPublisher) -> None:
        self._sources = sources
        self._publisher = publisher

    async def execute(self, command: ScrapeRequested, correlation_id: str | None = None) -> int:
        source = self._sources.get(command.source)
        if source is None:
            raise UnknownSourceError(f"fonte desconhecida: {command.source!r}")

        # Busca tudo antes de publicar: se a fonte falha no meio, nada sai
        exams = await source.fetch_exams()
        logger.info("concursos coletados", extra={"source": source.name, "count": len(exams)})

        for exam in exams:
            event = ExamDiscovered.from_exam(exam, correlation_id=correlation_id)
            await self._publisher.publish(
                event_id=event.event_id,
                event_type=event.event_type,
                body=event.model_dump_json().encode(),
            )

        logger.info("eventos publicados", extra={"source": source.name, "count": len(exams)})
        return len(exams)
