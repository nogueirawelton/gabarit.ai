"""PdfPages com o pypdf (Python puro). Ler um edital leva segundos de CPU: roda numa thread."""

import asyncio
import io
from collections.abc import Sequence

from pypdf import PdfReader, PdfWriter
from pypdf.errors import PyPdfError

from src.domain.errors import UnreadablePdfError


class PypdfPages:
    async def texts(self, content: bytes) -> list[str]:
        return await asyncio.to_thread(self._texts, content)

    async def subset(self, content: bytes, pages: Sequence[int]) -> bytes:
        return await asyncio.to_thread(self._subset, content, pages)

    @staticmethod
    def _texts(content: bytes) -> list[str]:
        try:
            return [page.extract_text() or "" for page in PdfReader(io.BytesIO(content)).pages]
        except (PyPdfError, ValueError, KeyError, TypeError) as e:
            raise UnreadablePdfError(f"PDF ilegível para o pypdf: {type(e).__name__}") from e

    @staticmethod
    def _subset(content: bytes, pages: Sequence[int]) -> bytes:
        try:
            reader = PdfReader(io.BytesIO(content))
            writer = PdfWriter()
            for page in pages:
                writer.add_page(reader.pages[page])
            # fontes e imagens repetidas entre páginas viram um objeto só
            writer.compress_identical_objects()
            output = io.BytesIO()
            writer.write(output)
            return output.getvalue()
        except (PyPdfError, ValueError, KeyError, TypeError, IndexError) as e:
            raise UnreadablePdfError(f"não deu para recortar o PDF: {type(e).__name__}") from e
