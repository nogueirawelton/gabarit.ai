class ScrapingUseCase:
    async def execute(self, data: dict):
        url = data.get("url")
        print(f"[Worker] Iniciando scrap da URL: {url}")
        # Lógica de processamento...