from typing import Protocol
from uuid import UUID


class EventPublisher(Protocol):
    async def publish(self, *, event_id: UUID, event_type: str, body: bytes) -> None:
        """Publica um evento já serializado. Só retorna depois que o broker confirmou."""
        ...
