from datetime import UTC, datetime
from uuid import UUID

import aio_pika
from aio_pika.abc import AbstractExchange, AbstractRobustConnection


class RabbitMQPublisher:
    """Publica no exchange topic de eventos com routing key = event_type."""

    def __init__(self, exchange: AbstractExchange) -> None:
        self._exchange = exchange

    @classmethod
    async def create(
        cls, connection: AbstractRobustConnection, exchange_name: str
    ) -> "RabbitMQPublisher":
        # canal próprio, com publisher confirms: o publish só retorna após o ack do broker
        channel = await connection.channel(publisher_confirms=True, on_return_raises=True)
        exchange = await channel.declare_exchange(
            exchange_name, aio_pika.ExchangeType.TOPIC, durable=True
        )
        return cls(exchange)

    async def publish(self, *, event_id: UUID, event_type: str, body: bytes) -> None:
        message = aio_pika.Message(
            body=body,
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            message_id=str(event_id),
            type=event_type,
            timestamp=datetime.now(UTC),
        )
        await self._exchange.publish(message, routing_key=event_type)
