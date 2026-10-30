from collections.abc import Sequence
from uuid import UUID

from pydantic import BaseModel

from src.domain.errors import DocumentNotFoundError, TransientError, UnreadablePdfError
from src.domain.extraction import Document, ExtractionOutput, Usage


class FakeDocumentStore:
    def __init__(self, documents: dict[str, bytes] | None = None, *, down: bool = False) -> None:
        self._documents = documents or {}
        self._down = down

    async def read(self, key: str) -> bytes:
        if self._down:
            raise TransientError("MinIO fora do ar")
        if key not in self._documents:
            raise DocumentNotFoundError(f"documento não encontrado: {key}")
        return self._documents[key]


class FakePublisher:
    def __init__(self) -> None:
        self.published: list[tuple[UUID, str, bytes]] = []

    async def publish(self, *, event_id: UUID, event_type: str, body: bytes) -> None:
        self.published.append((event_id, event_type, body))


class FakeLlm:
    """Devolve a saída configurada (ou uma de `results` por chamada, na ordem) e guarda o que
    recebeu."""

    def __init__(
        self,
        result: dict[str, object] | None = None,
        *,
        results: list[dict[str, object]] | None = None,
    ) -> None:
        self._results = results or [result or {}]
        self.calls: list[tuple[list[str], str, type[BaseModel], str]] = []

    async def extract(
        self,
        *,
        documents: Sequence[Document],
        instructions: str,
        output_model: type[BaseModel],
        effort: str,
    ) -> ExtractionOutput:
        self.calls.append(([d.sha256 for d in documents], instructions, output_model, effort))
        return ExtractionOutput(
            result=self._results[min(len(self.calls), len(self._results)) - 1],
            usage=Usage(input_tokens=1000, output_tokens=200, cache_read_input_tokens=900),
            model="claude-opus-5-5",
        )


class FakePdf:
    """Texto de cada página configurado; o recorte é um PDF falso com os números das páginas."""

    def __init__(self, pages: list[str] | None = None, *, broken: bool = False) -> None:
        self._pages = pages or []
        self._broken = broken
        self.subsets: list[list[int]] = []

    async def texts(self, content: bytes) -> list[str]:
        if self._broken:
            raise UnreadablePdfError("PDF protegido")
        return self._pages

    async def subset(self, content: bytes, pages: Sequence[int]) -> bytes:
        self.subsets.append(list(pages))
        return b"%PDF recorte " + ",".join(map(str, pages)).encode()
