import asyncio
import os

from infrastructure.adapters.messaging.rabbitmq_consumer import RabbitMQConsumerAdapter
from src.infrastructure.factories.ingestion_use_case import IngestionUseCaseFactory


async def main():
    amqp_url = os.getenv("RABBITMQ_URL")
    messaging_consumer = RabbitMQConsumerAdapter(amqp_url)

    ingestion_use_case = IngestionUseCaseFactory.create()

    print(" [*] Aguardando mensagens de scrap. Para sair pressione CTRL+C")

    await messaging_consumer.consume(
        queue_name="q_scraping_requests",
        callback=ingestion_use_case.execute
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass