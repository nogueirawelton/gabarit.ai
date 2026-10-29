"""As mensagens do serviço batem com os schemas em /contracts (compartilhados com o `core`)."""

import json
from pathlib import Path
from uuid import uuid4

import pytest
from jsonschema import Draft202012Validator
from pydantic import ValidationError

from src.application.messages import ExtractionCompleted, ExtractionFailed, ExtractionRequested
from src.domain.extraction import ExtractionOutput, Usage

# local: raiz do repo (algum diretório acima); container de dev: montado em /contracts
_CANDIDATES = [p / "contracts" for p in Path(__file__).resolve().parents]
CONTRACTS = next((p for p in _CANDIDATES if p.is_dir()), None)

pytestmark = pytest.mark.skipif(CONTRACTS is None, reason="pasta contracts/ não encontrada")

SHA = "a" * 64


def validator(relative: str) -> Draft202012Validator:
    assert CONTRACTS is not None
    schema = json.loads((CONTRACTS / relative).read_text(encoding="utf-8"))
    return Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)


def request() -> ExtractionRequested:
    return ExtractionRequested(
        extraction_id=uuid4(),
        task="ECHO",
        prompt_version="v1",
        documents=[{"sha256": SHA, "key": f"blobs/{SHA}.pdf"}],
        params={"cargo": "Analista"},
    )


def test_extraction_requested_bate_com_o_schema() -> None:
    validator("commands/extraction.requested.v1.json").validate(
        json.loads(request().model_dump_json())
    )


def test_extraction_requested_recusa_campo_desconhecido() -> None:
    body = json.loads(request().model_dump_json()) | {"x": 1}
    with pytest.raises(ValidationError):
        ExtractionRequested.model_validate(body)


def test_extraction_completed_bate_com_o_schema() -> None:
    output = ExtractionOutput(
        result={"params": {}, "documents": []},
        usage=Usage(input_tokens=10, output_tokens=2),
        model="claude-opus-5-5",
    )
    event = ExtractionCompleted.of(request(), output, correlation_id="cmd-1")

    validator("events/extraction.completed.v1.json").validate(json.loads(event.model_dump_json()))


def test_extraction_failed_bate_com_o_schema() -> None:
    event = ExtractionFailed.of(request(), "PDF ilegível", retryable=False, correlation_id=None)

    validator("events/extraction.failed.v1.json").validate(json.loads(event.model_dump_json()))
