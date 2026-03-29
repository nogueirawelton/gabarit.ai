import asyncio
import os

from src.application.use_cases.scraping_use_case import ScrapingUseCase
from src.infrastructure.adapters.rabbitmq_adapter import RabbitMQAdapter


async def main():
    amqp_url = os.getenv("RABBITMQ_URL")

    adapter = RabbitMQAdapter(amqp_url)
    use_case = ScrapingUseCase()

    print(" [*] Aguardando mensagens de scrap. Para sair pressione CTRL+C")

    await adapter.consume(
        queue_name="q_scraping_requests",
        callback=use_case.execute
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass