from domain.ports.scraper import ScraperPort
from src.infrastructure.logging.logger import logger

class ScrapingUseCase:
    def __init__(self, scraper: ScraperPort):
        self.scraper = scraper

    async def execute(self, data: dict):
        url = data.get("url")

        logger.info("[Worker] Iniciando scrap da URL: {url}")

        exams = await self.scraper.fetch_exams(url)

        print(exams)
