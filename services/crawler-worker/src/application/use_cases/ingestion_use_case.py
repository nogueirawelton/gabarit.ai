from src.domain.ports.scraper import ScraperPort

class IngestionUseCase:
    def __init__(self, scraper: ScraperPort):
        self.scraper = scraper

    async def execute(self, data: dict):
        url = data.get("url")

        exams = await self.scraper.fetch_exams(url)

        print(exams)
