"""Consumer de extraction.requested.

Topologia: a da infra (infra/rabbitmq/definitions.json), só conferida aqui (declaração passiva):
  exchange gabarit.commands (topic) --extraction.requested--> fila extraction.requests (quorum)
  fila extraction.requests --dead letter--> exchange gabarit.dlx --> fila extraction.requests.dlq

Erros:
  - mensagem malformada -> DLQ direto (não dá nem para responder: falta o extraction_id)
  - entrada inválida -> o use case publica extraction.failed; ack
  - transitório -> volta pra fila; na última entrega publica extraction.failed (retryable) e dá ack,
    para o core não ficar esperando um resultado que foi parar na DLQ
"""

import asyncio
import logging
from uuid import uuid4

from aio_pika.abc import AbstractIncomingMessage, AbstractQueue, AbstractRobustConnection
from pydantic import ValidationError

from src.application.messages import ExtractionRequested
from src.application.use_cases.process_extraction import ProcessExtraction
from src.infrastructure.logging import message_id_var

logger = logging.getLogger(__name__)


def is_last_delivery(headers: dict[str, object], max_deliveries: int) -> bool:
    """A quorum queue conta as devoluções em x-delivery-count (ausente na primeira entrega)."""
    returned = headers.get("x-delivery-count")
    previous = returned if isinstance(returned, int) else 0
    return previous + 1 >= max_deliveries


class RabbitMQConsumer:
    def __init__(
        self,
        connection: AbstractRobustConnection,
        use_case: ProcessExtraction,
        *,
        queue_name: str,
        prefetch: int,
        max_deliveries: int,
    ) -> None:
        self._connection = connection
        self._use_case = use_case
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
        # passiva: fila ausente (rabbitmq-init não rodou) quebra o boot em vez de criar outra
        self._queue = await channel.get_queue(self._queue_name, ensure=True)
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
            command = ExtractionRequested.model_validate_json(message.body)
        except ValidationError as e:
            logger.error("mensagem malformada, indo pra DLQ", extra={"error": str(e)})
            await message.reject(requeue=False)
            return

        try:
            await self._use_case.execute(command, correlation_id=message_id)
        except Exception as e:
            if not is_last_delivery(dict(message.headers), self._max_deliveries):
                logger.exception("erro transitório, devolvendo pra fila")
                await message.nack(requeue=True)
                return
            logger.exception("erro transitório na última tentativa, desistindo")
            try:
                await self._use_case.give_up(
                    command, f"falhou {self._max_deliveries} vezes: {e}", correlation_id=message_id
                )
            except Exception:
                # nem a falha saiu: o broker manda para a DLQ (x-delivery-limit)
                logger.exception("não foi possível publicar a falha")
                await message.nack(requeue=True)
                return
            await message.ack()
        else:
            # ack só no sucesso: se o container cair no meio, a mensagem volta
            await message.ack()
