from uuid import UUID

from src.domain.entities.exam import Exam
from src.domain.errors import SourceUnavailableError


class FakeExamSource:
    def __init__(self, exams: list[Exam], name: str = "fake", fail: bool = False) -> None:
        self._exams = exams
        self._name = name
        self._fail = fail

    @property
    def name(self) -> str:
        return self._name

    async def fetch_exams(self) -> list[Exam]:
        if self._fail:
            raise SourceUnavailableError("fonte fora do ar")
        return self._exams


class FakePublisher:
    def __init__(self) -> None:
        self.published: list[tuple[UUID, str, bytes]] = []

    async def publish(self, *, event_id: UUID, event_type: str, body: bytes) -> None:
        self.published.append((event_id, event_type, body))
