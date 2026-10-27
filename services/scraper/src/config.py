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

    pci_mcp_url: str = "https://mcp.pciconcursos.com.br/mcp"
    pci_mcp_timeout_s: float = 120.0

    @property
    def rabbitmq_url(self) -> str:
        user = quote(self.rabbitmq_user, safe="")
        password = quote(self.rabbitmq_password.get_secret_value(), safe="")
        vhost = quote(self.rabbitmq_vhost, safe="")
        return f"amqp://{user}:{password}@{self.rabbitmq_host}:{self.rabbitmq_port}/{vhost}"
