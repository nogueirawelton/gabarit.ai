"""Consumer de scrape.requested.

Topologia (declarada aqui, idempotente):
  exchange gabarit.commands (topic) --scrape.requested--> fila <queue> (quorum)
  fila <queue> --dead letter--> exchange gabarit.dlx (direct) --<queue>--> fila <queue>.dlq

Erros:
  - mensagem malformada / fonte desconhecida / formato da fonte mudou -> DLQ direto
  - transitório (fonte fora do ar...) -> volta pra fila; após `max_deliveries` vai pra DLQ
"""

import asyncio
import logging
from uuid import uuid4

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractQueue, AbstractRobustConnection
from pydantic import ValidationError

from src.application.messages import ScrapeRequested
from src.application.use_cases.scrape_exam import ScrapeExams
from src.domain.errors import InvalidSourceDataError, UnknownSourceError
from src.infrastructure.logging import message_id_var

logger = logging.getLogger(__name__)

ROUTING_KEY = "scrape.requested"
DLX_NAME = "gabarit.dlx"


class RabbitMQConsumer:
    def __init__(
        self,
        connection: AbstractRobustConnection,
        use_case: ScrapeExams,
        *,
        commands_exchange: str,
        queue_name: str,
        prefetch: int,
        max_deliveries: int,
    ) -> None:
        self._connection = connection
        self._use_case = use_case
        self._commands_exchange = commands_exchange
        self._queue_name = queue_name
        self._prefetch = prefetch
        self._max_deliveries = max_deliveries
        self._queue: AbstractQueue | None = None
        self._consumer_tag: str | None = None
        self._in_flight = 0
        self._idle = asyncio.Event()
        self._idle.set()

    async def start(self) -> None:
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=self._prefetch)

        dlx = await channel.declare_exchange(DLX_NAME, aio_pika.ExchangeType.DIRECT, durable=True)
        dlq = await channel.declare_queue(
            f"{self._queue_name}.dlq", durable=True, arguments={"x-queue-type": "quorum"}
        )
        await dlq.bind(dlx, routing_key=self._queue_name)

        commands = await channel.declare_exchange(
            self._commands_exchange, aio_pika.ExchangeType.TOPIC, durable=True
        )
        self._queue = await channel.declare_queue(
            self._queue_name,
            durable=True,
            arguments={
                "x-queue-type": "quorum",
                # quorum queue conta as entregas e manda pra DLX ao passar do limite
                "x-delivery-limit": self._max_deliveries,
                "x-dead-letter-exchange": DLX_NAME,
                "x-dead-letter-routing-key": self._queue_name,
            },
        )
        await self._queue.bind(commands, routing_key=ROUTING_KEY)
        self._consumer_tag = await self._queue.consume(self._on_message)
        logger.info("consumindo", extra={"queue": self._queue_name})

    async def stop(self) -> None:
        """Para de receber e espera a mensagem em processamento terminar (graceful shutdown)."""
        if self._queue and self._consumer_tag:
            await self._queue.cancel(self._consumer_tag)
        if not self._idle.is_set():
            logger.info("aguardando processamento em andamento")
            await self._idle.wait()

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        self._in_flight += 1
        self._idle.clear()
        # sem message_id (ex.: publicado à mão no management) gera um para rastrear logs/eventos;
        # o gerado muda a cada reentrega (daí o prefixo): quem publica deve mandar o seu
        message_id = message.message_id or f"gen-{uuid4()}"
        token = message_id_var.set(message_id)
        try:
            await self._handle(message, message_id)
        finally:
            message_id_var.reset(token)
            self._in_flight -= 1
            if self._in_flight == 0:
                self._idle.set()

    async def _handle(self, message: AbstractIncomingMessage, message_id: str) -> None:
        try:
            command = ScrapeRequested.model_validate_json(message.body)
        except ValidationError as e:
            logger.error("mensagem malformada, indo pra DLQ", extra={"error": str(e)})
            await message.reject(requeue=False)
            return

        try:
            await self._use_case.execute(command, correlation_id=message_id)
        except (UnknownSourceError, InvalidSourceDataError) as e:
            logger.error("erro permanente, indo pra DLQ", extra={"error": str(e)})
            await message.reject(requeue=False)
        except Exception:
            logger.exception("erro transitório, devolvendo pra fila")
            await message.nack(requeue=True)
        else:
            # ack só no sucesso: se o container cair no meio, a mensagem volta
            await message.ack()
