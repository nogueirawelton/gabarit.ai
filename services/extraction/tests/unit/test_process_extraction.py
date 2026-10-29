import hashlib
import json
from uuid import uuid4

import pytest

from src.application.messages import ExtractionRequested
from src.application.tasks.edital_overview import EditalOverviewV1
from src.application.use_cases.process_extraction import ProcessExtraction
from src.domain.errors import TransientError
from tests.fakes import FakeDocumentStore, FakeLlm, FakePublisher

PDF = b"%PDF-1.7 edital"
SHA = hashlib.sha256(PDF).hexdigest()
KEY = f"blobs/{SHA}.pdf"


def command(**overrides: object) -> ExtractionRequested:
    fields: dict[str, object] = {
        "extraction_id": uuid4(),
        "task": "ECHO",
        "prompt_version": "v1",
        "documents": [{"sha256": SHA, "key": KEY}],
        "params": {"cargo": "Analista"},
    }
    return ExtractionRequested.model_validate(fields | overrides)


def only_event(publisher: FakePublisher) -> tuple[str, dict]:
    assert len(publisher.published) == 1
    _, event_type, body = publisher.published[0]
    return event_type, json.loads(body)


async def test_echo_le_os_documentos_e_devolve_os_parametros() -> None:
    publisher = FakePublisher()
    cmd = command()

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), FakeLlm(), publisher).execute(
        cmd, "msg-1"
    )

    event_type, event = only_event(publisher)
    assert event_type == "extraction.completed"
    assert event["correlation_id"] == "msg-1"
    assert event["data"]["extraction_id"] == str(cmd.extraction_id)
    assert event["data"]["model"] is None
    assert event["data"]["result"] == {
        "params": {"cargo": "Analista"},
        "documents": [{"sha256": SHA, "size_bytes": len(PDF)}],
    }
    assert event["data"]["usage"]["input_tokens"] == 0


@pytest.mark.parametrize(
    ("store", "cmd", "reason"),
    [
        (FakeDocumentStore({KEY: PDF}), command(prompt_version="v9"), "versão de prompt"),
        (FakeDocumentStore(), command(), "não encontrado"),
        (FakeDocumentStore({KEY: b"outro conteudo"}), command(), "não confere"),
        (
            FakeDocumentStore({KEY: PDF}),
            command(task="EDITAL_OVERVIEW", documents=[]),
            "recebe 1 documento",
        ),
    ],
)
async def test_entrada_invalida_vira_falha_definitiva(
    store: FakeDocumentStore, cmd: ExtractionRequested, reason: str
) -> None:
    publisher = FakePublisher()

    await ProcessExtraction(store, FakeLlm(), publisher).execute(cmd)

    event_type, event = only_event(publisher)
    assert event_type == "extraction.failed"
    assert reason in event["data"]["reason"]
    assert event["data"]["retryable"] is False


async def test_erro_transitorio_sobe_sem_publicar() -> None:
    publisher = FakePublisher()

    with pytest.raises(TransientError):
        await ProcessExtraction(FakeDocumentStore(down=True), FakeLlm(), publisher).execute(
            command()
        )

    assert publisher.published == []


async def test_desistir_publica_falha_que_pode_ser_repetida() -> None:
    publisher = FakePublisher()

    await ProcessExtraction(FakeDocumentStore(), FakeLlm(), publisher).give_up(
        command(), "MinIO fora"
    )

    event_type, event = only_event(publisher)
    assert event_type == "extraction.failed"
    assert event["data"]["retryable"] is True


async def test_tarefa_com_llm_manda_o_documento_e_publica_a_saida() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"organ": "TJ-SP", "positions": []})

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher).execute(
        command(task="EDITAL_OVERVIEW", params={})
    )

    [(shas, instructions, output_model, effort)] = llm.calls
    assert shas == [SHA]
    assert "edital" in instructions
    assert output_model is EditalOverviewV1
    assert effort == "high"
    event_type, event = only_event(publisher)
    assert event_type == "extraction.completed"
    assert event["data"]["model"] == "claude-opus-5-5"
    assert event["data"]["result"]["organ"] == "TJ-SP"
    assert event["data"]["usage"]["cache_read_input_tokens"] == 900
