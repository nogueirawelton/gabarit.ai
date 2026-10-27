from uuid import UUID

from src.domain.entities.catalog_position import CatalogPosition
from src.domain.entities.exam import Exam
from src.domain.errors import SourceUnavailableError
from src.domain.ports.exam_source import SourceCollection


class FakeExamSource:
    def __init__(
        self,
        exams: list[Exam],
        name: str = "fake",
        fail: bool = False,
        catalog: tuple[CatalogPosition, ...] = (),
    ) -> None:
        self._exams = exams
        self._name = name
        self._fail = fail
        self._catalog = catalog

    @property
    def name(self) -> str:
        return self._name

    async def collect(self) -> SourceCollection:
        if self._fail:
            raise SourceUnavailableError("fonte fora do ar")
        return SourceCollection(exams=tuple(self._exams), catalog=self._catalog)


class FakePublisher:
    def __init__(self) -> None:
        self.published: list[tuple[UUID, str, bytes]] = []

    async def publish(self, *, event_id: UUID, event_type: str, body: bytes) -> None:
        self.published.append((event_id, event_type, body))
