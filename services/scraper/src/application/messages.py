"""Mensagens que entram e saem do serviço. Espelham os schemas em /contracts (fonte da verdade)."""

from collections.abc import Iterable
from datetime import UTC, date, datetime
from typing import Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field

from src.domain.entities.catalog_position import CatalogPosition
from src.domain.entities.exam import Exam


class ScrapeRequested(BaseModel):
    """Comando de entrada (contracts/commands/scrape.requested.v1.json)."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    source: str = Field(min_length=1)


class PositionData(BaseModel):
    name: str
    catalog_slugs: list[str]


class ExamData(BaseModel):
    source: str
    external_id: str
    title: str
    url: str
    uf: str | None
    region: str | None
    education_level: str | None
    openings: str | None
    registration_start: date | None
    registration_end: date | None
    registration_note: str | None
    positions: list[PositionData]


class ExamDiscovered(BaseModel):
    """Evento de saída (contracts/events/exam.discovered.v1.json).

    O concurso vai inteiro, com os cargos dentro: o `core` faz upsert por (source, external_id).
    """

    model_config = ConfigDict(frozen=True)

    event_id: UUID = Field(default_factory=uuid4)
    event_type: Literal["exam.discovered"] = "exam.discovered"
    version: Literal[1] = 1
    occurred_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    # message_id do comando que originou a coleta, para rastrear ponta a ponta
    correlation_id: str | None = None
    data: ExamData

    @classmethod
    def from_exam(cls, exam: Exam, correlation_id: str | None = None) -> "ExamDiscovered":
        return cls(
            correlation_id=correlation_id,
            data=ExamData(
                **exam.model_dump(exclude={"positions"}),
                positions=[
                    PositionData(name=p.name, catalog_slugs=list(p.catalog_slugs))
                    for p in exam.positions
                ],
            ),
        )


class CatalogPositionData(BaseModel):
    slug: str
    name: str


class PositionCatalogData(BaseModel):
    source: str
    positions: list[CatalogPositionData]


class PositionCatalogDiscovered(BaseModel):
    """Evento de saída (contracts/events/position_catalog.discovered.v1.json).

    O catálogo inteiro da fonte num evento só: o `core` faz upsert por (source, slug).
    """

    model_config = ConfigDict(frozen=True)

    event_id: UUID = Field(default_factory=uuid4)
    event_type: Literal["position_catalog.discovered"] = "position_catalog.discovered"
    version: Literal[1] = 1
    occurred_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    correlation_id: str | None = None
    data: PositionCatalogData

    @classmethod
    def from_catalog(
        cls, source: str, catalog: Iterable[CatalogPosition], correlation_id: str | None = None
    ) -> "PositionCatalogDiscovered":
        return cls(
            correlation_id=correlation_id,
            data=PositionCatalogData(
                source=source,
                positions=[CatalogPositionData(slug=c.slug, name=c.name) for c in catalog],
            ),
        )
