from typing import Callable, Awaitable
from src.infrastructure.logging.logger import logger

import aio_pika
import json
from src.domain.ports.messagins import MessageConsumerPort


class RabbitMQConsumerAdapter(MessageConsumerPort):
    def __init__(self, amqp_url: str):
        self.amqp_url = amqp_url

    async def consume(self, queue_name: str, callback: Callable[[dict], Awaitable[None]]) -> None:
        connection = await aio_pika.connect_robust(self.amqp_url)
        async with connection:
            channel = await connection.channel()
            await channel.set_qos(prefetch_count=1)

            exchange = await channel.declare_exchange(
                "ex_gabarit_events",
                type=aio_pika.ExchangeType.DIRECT,
                durable=True
            )

            queue = await channel.declare_queue(queue_name, durable=True)

            await queue.bind(exchange, routing_key="scrap.start")

            async with queue.iterator() as queue_iter:
                async for message in queue_iter:
                    async with message.process(requeue=False):
                        try:
                            payload = json.loads(message.body.decode())
                            await callback(payload)
                        except Exception as e:
                            logger.error("Falha ao executar Scraping", error=e, payload=payload )