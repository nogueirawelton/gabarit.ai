from typing import Protocol, Any, Callable, Awaitable

class MessageConsumerPort(Protocol):
    async def consume(self, queue_name: str, callback: Callable[[Any], Awaitable[None]]) -> None:
        """Porta para consumo de mensagens assíncronas."""