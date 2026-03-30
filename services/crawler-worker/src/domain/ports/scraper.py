from typing import Protocol, List

from domain.entities.exam import Exam


class ScraperPort(Protocol):
    async def fetch_exams(self, url: str)-> List[Exam]:
        ...