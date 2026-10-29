from collections.abc import Sequence
from typing import Protocol


class PdfPages(Protocol):
    async def texts(self, content: bytes) -> list[str]:
        """Texto de cada página, na ordem ("" se a página não tem texto). Levanta
        UnreadablePdfError se não conseguir abrir o PDF."""
        ...

    async def subset(self, content: bytes, pages: Sequence[int]) -> bytes:
        """PDF só com estas páginas (a partir de 0), na ordem. Levanta UnreadablePdfError."""
        ...
