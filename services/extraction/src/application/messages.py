"""Mensagens que entram e saem do serviço. Espelham os schemas em /contracts (fonte da verdade)."""

from datetime import UTC, datetime
from typing import Literal
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field

from src.domain.extraction import DocumentRef, ExtractionOutput, Task, Usage


class ExtractionRequested(BaseModel):
    """Comando de entrada (contracts/commands/extraction.requested.v1.json)."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    extraction_id: UUID
    task: Task
    prompt_version: str = Field(pattern=r"^v[0-9]+$")
    documents: tuple[DocumentRef, ...]
    params: dict[str, object]


class CompletedData(BaseModel):
    extraction_id: UUID
    task: str
    prompt_version: str
    model: str | None
    result: dict[str, object]
    usage: Usage


class ExtractionCompleted(BaseModel):
    """Evento de saída (contracts/events/extraction.completed.v1.json)."""

    model_config = ConfigDict(frozen=True)

    event_id: UUID = Field(default_factory=uuid4)
    event_type: Literal["extraction.completed"] = "extraction.completed"
    version: Literal[1] = 1
    occurred_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    # message_id do comando, para rastrear ponta a ponta
    correlation_id: str | None = None
    data: CompletedData

    @classmethod
    def of(
        cls,
        command: ExtractionRequested,
        output: ExtractionOutput,
        correlation_id: str | None = None,
    ) -> "ExtractionCompleted":
        return cls(
            correlation_id=correlation_id,
            data=CompletedData(
                extraction_id=command.extraction_id,
                task=command.task,
                prompt_version=command.prompt_version,
                model=output.model,
                result=output.result,
                usage=output.usage,
            ),
        )


class FailedData(BaseModel):
    extraction_id: UUID
    task: str
    prompt_version: str
    reason: str = Field(min_length=1)
    retryable: bool


class ExtractionFailed(BaseModel):
    """Evento de saída (contracts/events/extraction.failed.v1.json)."""

    model_config = ConfigDict(frozen=True)

    event_id: UUID = Field(default_factory=uuid4)
    event_type: Literal["extraction.failed"] = "extraction.failed"
    version: Literal[1] = 1
    occurred_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    correlation_id: str | None = None
    data: FailedData

    @classmethod
    def of(
        cls,
        command: ExtractionRequested,
        reason: str,
        *,
        retryable: bool,
        correlation_id: str | None = None,
    ) -> "ExtractionFailed":
        return cls(
            correlation_id=correlation_id,
            data=FailedData(
                extraction_id=command.extraction_id,
                task=command.task,
                prompt_version=command.prompt_version,
                reason=reason,
                retryable=retryable,
            ),
        )
