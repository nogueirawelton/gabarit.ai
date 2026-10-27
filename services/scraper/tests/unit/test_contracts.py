"""As mensagens do serviço batem com os schemas em /contracts (compartilhados com o `exam`)."""

import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator
from pydantic import ValidationError

from src.application.messages import ExamDiscovered, ScrapeRequested
from src.infrastructure.sources.pci_mcp import parse_listing

# local: raiz do repo (algum diretório acima); container de dev: montado em /contracts
_CANDIDATES = [p / "contracts" for p in Path(__file__).resolve().parents]
CONTRACTS = next((p for p in _CANDIDATES if p.is_dir()), None)
FIXTURES = Path(__file__).parent.parent / "fixtures" / "pci"

pytestmark = pytest.mark.skipif(CONTRACTS is None, reason="pasta contracts/ não encontrada")


def validator(relative: str) -> Draft202012Validator:
    assert CONTRACTS is not None
    schema = json.loads((CONTRACTS / relative).read_text(encoding="utf-8"))
    return Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER)


def test_exam_discovered_bate_com_o_schema() -> None:
    v = validator("events/exam.discovered.v1.json")
    exams = parse_listing((FIXTURES / "listar_concursos.json").read_text(encoding="utf-8"))

    for exam in exams:
        event = json.loads(ExamDiscovered.from_exam(exam, correlation_id="cmd-1").model_dump_json())
        v.validate(event)


def test_scrape_requested_bate_com_o_schema() -> None:
    v = validator("commands/scrape.requested.v1.json")
    v.validate(json.loads(ScrapeRequested(source="pciconcursos").model_dump_json()))


def test_scrape_requested_recusa_campo_desconhecido() -> None:
    with pytest.raises(ValidationError):
        ScrapeRequested.model_validate_json('{"source": "pciconcursos", "x": 1}')
