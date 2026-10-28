"""Único lugar que lê variáveis de ambiente. Falta de variável obrigatória quebra no boot."""

from urllib.parse import quote

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", frozen=True)

    log_level: str = "INFO"

    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str
    rabbitmq_password: SecretStr
    rabbitmq_vhost: str = "/"

    commands_exchange: str = "gabarit.commands"
    events_exchange: str = "gabarit.events"
    scrape_queue: str = "scraper.scrape_requested"
    # uma coleta por vez: cada uma já faz várias chamadas à fonte
    prefetch: int = 1
    max_deliveries: int = 3

    # comportamento HTTP do scraper, igual para qualquer fonte coletada
    # pausa entre requisições (uma por vez). A coleta do PCI faz ~500 requisições (~10 min com 1s)
    scraper_request_delay_s: float = 1.0
    # identifica o robô e dá um contato para o site (boa prática de scraping)
    scraper_user_agent: str = (
        "gabarit-ai-scraper/0.1 (+https://github.com/nogueirawelton/gabarit.ai)"
    )
    scraper_timeout_s: float = 30.0
    # novas tentativas em erro transitório (rede, 429, 5xx)
    scraper_max_retries: int = 3

    # fontes
    pci_base_url: str = "https://www.pciconcursos.com.br"

    @property
    def rabbitmq_url(self) -> str:
        user = quote(self.rabbitmq_user, safe="")
        password = quote(self.rabbitmq_password.get_secret_value(), safe="")
        vhost = quote(self.rabbitmq_vhost, safe="")
        return f"amqp://{user}:{password}@{self.rabbitmq_host}:{self.rabbitmq_port}/{vhost}"
