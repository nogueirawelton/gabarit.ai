from typing import Protocol


class DocumentStore(Protocol):
    async def read(self, key: str) -> bytes:
        """Conteúdo inteiro do documento.

        Levanta DocumentNotFoundError (permanente) ou TransientError (armazenamento fora do ar).
        """
        ...
