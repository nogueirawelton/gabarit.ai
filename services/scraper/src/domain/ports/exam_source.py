from typing import Protocol

from pydantic import BaseModel, ConfigDict

from src.domain.entities.catalog_position import CatalogPosition
from src.domain.entities.exam import Exam


class SourceCollection(BaseModel):
    """Tudo o que uma coleta completa trouxe da fonte."""

    model_config = ConfigDict(frozen=True)

    exams: tuple[Exam, ...]
    # vazio quando a fonte não tem catálogo de cargos
    catalog: tuple[CatalogPosition, ...] = ()


class ExamSource(Protocol):
    """Fonte de concursos (uma banca, um agregador...)."""

    @property
    def name(self) -> str: ...

    async def collect(self) -> SourceCollection:
        """Coleta completa: todos os concursos abertos (com cargos) e o catálogo de cargos.

        Levanta SourceUnavailableError (transitório) ou InvalidSourceDataError (formato mudou).
        """
        ...
