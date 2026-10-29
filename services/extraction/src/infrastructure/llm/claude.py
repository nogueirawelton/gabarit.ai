"""LlmClient com a API do Claude (SDK oficial `anthropic`).

- PDF pela Files API: sobe uma vez por conteúdo (sha256) e reaproveita o file_id; o arquivo expira
  sozinho na Anthropic (`expires_in_seconds`), sem limpeza nossa.
- Sem cache de prompt: cada tarefa manda só as páginas do edital que usa (recortes diferentes), e
  gravar no cache custa mais que mandar o PDF sem ele. Só compensa com duas tarefas sobre o mesmo
  arquivo em poucos minutos; se aparecer uma, `cache_control` no último documento.
- Saída estruturada pelo modelo pydantic da tarefa (`output_format`), em streaming (edital grande =
  resposta longa; sem streaming a requisição estoura o timeout HTTP).
- `fallbacks="default"`: se o modelo recusar por engano (classificadores de segurança), a própria
  API refaz o pedido num modelo de reserva; o modelo que respondeu vai em `model`.
"""

import logging
import time
from collections.abc import Sequence

import anthropic
from pydantic import BaseModel, ValidationError

from src.domain.errors import InvalidInputError, TransientError
from src.domain.extraction import Document, ExtractionOutput, Usage
from src.domain.ports.llm_client import Effort

logger = logging.getLogger(__name__)

# 128K é o máximo de saída; o que conta é o que o modelo gera (a lista de cargos de um edital grande
# passa fácil de 16K). Só é possível com streaming
MAX_TOKENS = 64_000

FALLBACK_BETA = "server-side-fallback-2026-07-01"


class ClaudeClient:
    def __init__(
        self,
        client: anthropic.AsyncAnthropic,
        *,
        model: str,
        system_prompt: str,
        file_ttl_s: int,
    ) -> None:
        self._client = client
        self._model = model
        self._system = system_prompt
        self._file_ttl_s = file_ttl_s
        # sha256 → (file_id, até quando reaproveitar). Some no restart: aí sobe de novo (é grátis;
        # o arquivo antigo expira sozinho)
        self._files: dict[str, tuple[str, float]] = {}

    async def extract(
        self,
        *,
        documents: Sequence[Document],
        instructions: str,
        output_model: type[BaseModel],
        effort: Effort,
    ) -> ExtractionOutput:
        content: list[dict[str, object]] = []
        for document in documents:
            file_id = await self._file_id(document)
            content.append({"type": "document", "source": {"type": "file", "file_id": file_id}})
        content.append({"type": "text", "text": instructions})

        try:
            async with self._client.beta.messages.stream(
                model=self._model,
                max_tokens=MAX_TOKENS,
                system=self._system,
                messages=[{"role": "user", "content": content}],
                thinking={"type": "adaptive"},
                output_config={"effort": effort},
                output_format=output_model,
                betas=[FALLBACK_BETA],
                fallbacks="default",
            ) as stream:
                message = await stream.get_final_message()
        except ValidationError as e:
            raise InvalidInputError(f"resposta fora do schema: {e.error_count()} erro(s)") from e
        except anthropic.APIError as e:
            raise _translate(e) from e

        extra = {
            "message_id_claude": message.id,
            "model": message.model,
            "stop_reason": message.stop_reason,
        }
        if message.stop_reason == "refusal":
            category = message.stop_details.category if message.stop_details else None
            logger.warning("o modelo recusou", extra=extra | {"category": category})
            raise InvalidInputError(f"o modelo recusou o documento (categoria: {category})")
        if message.stop_reason == "max_tokens":
            logger.warning("resposta cortada", extra=extra)
            raise InvalidInputError("resposta cortada no limite de tokens")
        if message.parsed_output is None:
            logger.warning("resposta sem saída estruturada", extra=extra)
            raise InvalidInputError(f"resposta sem saída estruturada ({message.stop_reason})")

        usage = message.usage
        logger.info(
            "resposta do Claude",
            extra=extra
            | {
                "input_tokens": usage.input_tokens,
                "output_tokens": usage.output_tokens,
                "cache_read_input_tokens": usage.cache_read_input_tokens,
                "cache_creation_input_tokens": usage.cache_creation_input_tokens,
            },
        )
        return ExtractionOutput(
            result=message.parsed_output.model_dump(mode="json"),
            usage=Usage(
                input_tokens=usage.input_tokens,
                output_tokens=usage.output_tokens,
                cache_read_input_tokens=usage.cache_read_input_tokens or 0,
                cache_creation_input_tokens=usage.cache_creation_input_tokens or 0,
            ),
            model=message.model,
        )

    async def _file_id(self, document: Document) -> str:
        cached = self._files.get(document.sha256)
        if cached and cached[1] > time.monotonic():
            return cached[0]
        try:
            uploaded = await self._client.files.upload(
                file=(f"{document.sha256}.pdf", document.content, "application/pdf"),
                expires_in_seconds=self._file_ttl_s,
            )
        except anthropic.APIError as e:
            raise _translate(e) from e
        # reaproveita só até ~80% da validade: um pedido longo não pode pegar o arquivo sumindo
        self._files[document.sha256] = (uploaded.id, time.monotonic() + self._file_ttl_s * 0.8)
        logger.info(
            "PDF enviado à Files API", extra={"sha256": document.sha256, "file_id": uploaded.id}
        )
        return uploaded.id


def _translate(error: anthropic.APIError) -> Exception:
    """Erro do SDK → erro do domínio. O SDK já tentou de novo (429, 5xx, rede) antes daqui."""
    if isinstance(error, anthropic.APIConnectionError | anthropic.RateLimitError):
        return TransientError(f"API do Claude indisponível: {type(error).__name__}")
    if isinstance(error, anthropic.AuthenticationError | anthropic.PermissionDeniedError):
        # configuração (chave inválida, sem acesso ao modelo), não o documento
        return TransientError(f"API do Claude recusou a credencial: {error.status_code}")
    if isinstance(error, anthropic.APIStatusError):
        if error.status_code >= 500 or error.status_code in (408, 409):
            return TransientError(f"API do Claude respondeu {error.status_code}")
        # 400/413/422: o pedido em si (PDF ilegível, grande demais, protegido por senha...)
        return InvalidInputError(
            f"API do Claude recusou o pedido ({error.status_code}): {error.message}"
        )
    return TransientError(f"erro na API do Claude: {type(error).__name__}")
