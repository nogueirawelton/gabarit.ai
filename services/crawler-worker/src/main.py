import asyncio
import os

from infrastructure.adapters.services.scraper import Scraper
from src.application.use_cases.scraping_use_case import ScrapingUseCase
from infrastructure.adapters.messaging.rabbitmq_consumer import RabbitMQConsumer


async def main():
    amqp_url = os.getenv("RABBITMQ_URL")

    messaging_consumer = RabbitMQConsumer(amqp_url)
    scraper = Scraper()

    use_case = ScrapingUseCase(scraper)

    print(" [*] Aguardando mensagens de scrap. Para sair pressione CTRL+C")

    await messaging_consumer.consume(
        queue_name="q_scraping_requests",
        callback=use_case.execute
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass