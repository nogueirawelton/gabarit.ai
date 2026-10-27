import asyncio
import logging
import signal

from src.config import Settings
from src.infrastructure.container import build_consumer
from src.infrastructure.logging import configure_logging

logger = logging.getLogger(__name__)


async def main() -> None:
    settings = Settings()  # type: ignore[call-arg]  # obrigatórios vêm do ambiente
    configure_logging(settings.log_level)

    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGTERM, signal.SIGINT):
        loop.add_signal_handler(sig, stop.set)

    async with build_consumer(settings) as consumer:
        await consumer.start()
        logger.info("scraper pronto")
        await stop.wait()
        logger.info("encerrando")
        await consumer.stop()


if __name__ == "__main__":
    asyncio.run(main())
