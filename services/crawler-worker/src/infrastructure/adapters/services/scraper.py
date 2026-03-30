import httpx
import asyncio

from typing import List

from pydantic import HttpUrl

from domain.entities.exam import Exam, ExamDocument
from domain.ports.scraper import ScraperPort
from selectolax.lexbor import LexborHTMLParser

from infrastructure.logging.logger import logger

class Scraper(ScraperPort):
    def __init__(self):
        self.headers = {
            "User-Agent": "GabaritAI-Crawler/1.0"
        }
        self.semaphore = asyncio.Semaphore(10)

    async def fetch_exams(self, url: str) -> List[Exam]:
        async with httpx.AsyncClient(timeout=20.0, follow_redirects=True) as client:
            response = await client.get(url, headers=self.headers)
            response.raise_for_status()
            parser = LexborHTMLParser(response.text)

            tasks = []
            for node in parser.css("#concursos [data-url]"):
                title_node = node.css_first("a")
                if title_node:
                    detail_url = title_node.attributes.get("href", "")
                    institution = title_node.text(strip=True)

                    tasks.append(self._fetch_detail(client, detail_url, institution))

            results = await asyncio.gather(*tasks, return_exceptions=True)

            valid_results = [r for r in results if isinstance(r, Exam)]
            return valid_results

    async def _fetch_detail(self, client: httpx.AsyncClient, url: str, institution: str) -> Exam | None:
        async with self.semaphore:
            try:
                response = await client.get(url, headers=self.headers)
                if response.status_code != 200:
                    return None

                detail_parser = LexborHTMLParser(response.text)

                pdf_links = []
                for link in detail_parser.css("#links a[href$='.pdf']"):
                    pdf_links.append(ExamDocument(url=link.attributes.get("href", ""), title=link.text(strip=True)))

                return Exam(url=HttpUrl(url), institution=institution, documents=pdf_links)
            except Exception as e:
                logger.error(f"Falha ao buscar notícia em {url}", error=e)
                return None