from collections.abc import Sequence
from typing import Literal, Protocol

from pydantic import BaseModel

from src.domain.extraction import Document, ExtractionOutput

Effort = Literal["low", "medium", "high", "xhigh", "max"]


class LlmClient(Protocol):
    async def extract(
        self,
        *,
        documents: Sequence[Document],
        instructions: str,
        output_model: type[BaseModel],
        effort: Effort,
    ) -> ExtractionOutput:
        """Lê os documentos e devolve a saída no formato de `output_model`, já validada.

        Levanta InvalidInputError (documento ilegível, recusa, resposta cortada ou fora do schema)
        ou TransientError (limite de uso, API fora, rede).
        """
        ...
