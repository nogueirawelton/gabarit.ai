"""Único lugar que lê variáveis de ambiente. Falta de variável obrigatória quebra no boot."""

from urllib.parse import quote

from pydantic import AnyHttpUrl, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", frozen=True)

    log_level: str = "INFO"

    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str
    rabbitmq_password: SecretStr
    rabbitmq_vhost: str = "/"

    events_exchange: str = "gabarit.events"
    # declarada pela infra (infra/rabbitmq/definitions.json), não aqui
    requests_queue: str = "extraction.requests"
    # um pedido por vez: cada um segura os PDFs em memória (até 30 MB cada)
    prefetch: int = 1
    # igual ao x-delivery-limit da fila: na última tentativa o worker desiste e publica a falha
    max_deliveries: int = 3

    # MinIO: só leitura de documents/blobs/ (usuário `extraction`, infra/minio/init.sh)
    minio_url: AnyHttpUrl = AnyHttpUrl("http://localhost:9000")
    minio_access_key: str = "extraction"
    minio_secret_key: SecretStr
    documents_bucket: str = "documents"

    # Claude: a chave só existe neste serviço
    anthropic_api_key: SecretStr = Field(min_length=1)
    claude_model: str = "claude-opus-5-5"
    # quanto tempo o PDF fica na Files API (expira sozinho); o worker reaproveita o file_id até ~80%
    files_ttl_s: int = 24 * 60 * 60

    @property
    def rabbitmq_url(self) -> str:
        user = quote(self.rabbitmq_user, safe="")
        password = quote(self.rabbitmq_password.get_secret_value(), safe="")
        vhost = quote(self.rabbitmq_vhost, safe="")
        return f"amqp://{user}:{password}@{self.rabbitmq_host}:{self.rabbitmq_port}/{vhost}"
