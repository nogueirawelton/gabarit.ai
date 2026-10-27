from typing import Protocol

from src.domain.entities.exam import Exam


class ExamSource(Protocol):
    """Fonte de concursos (uma banca, um agregador...)."""

    @property
    def name(self) -> str: ...

    async def fetch_exams(self) -> list[Exam]:
        """Devolve todos os concursos da fonte, cada um com seus cargos.

        Levanta SourceUnavailableError (transitório) ou InvalidSourceDataError (formato mudou).
        """
        ...
