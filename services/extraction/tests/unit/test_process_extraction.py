import hashlib
import json
from uuid import uuid4

import pytest

from src.application.messages import ExtractionRequested
from src.application.tasks import LLM_TASKS
from src.application.tasks.edital_overview import EditalOverviewV1
from src.application.tasks.edital_syllabus import EditalSyllabusV1
from src.application.tasks.exam_paper import ExamPaperV1
from src.application.tasks.question_topics import QuestionTopicsV1
from src.application.tasks.topic_questions import TopicQuestionsV1
from src.application.tasks.topic_summary import TopicSummaryV1
from src.application.use_cases.process_extraction import ProcessExtraction
from src.domain.errors import TransientError
from src.domain.extraction import Task
from tests.fakes import FakeDocumentStore, FakeLlm, FakePdf, FakePublisher

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

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), FakeLlm(), publisher, FakePdf()).execute(
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

    await ProcessExtraction(store, FakeLlm(), publisher, FakePdf()).execute(cmd)

    event_type, event = only_event(publisher)
    assert event_type == "extraction.failed"
    assert reason in event["data"]["reason"]
    assert event["data"]["retryable"] is False


async def test_erro_transitorio_sobe_sem_publicar() -> None:
    publisher = FakePublisher()

    with pytest.raises(TransientError):
        await ProcessExtraction(
            FakeDocumentStore(down=True), FakeLlm(), publisher, FakePdf()
        ).execute(command())

    assert publisher.published == []


async def test_desistir_publica_falha_que_pode_ser_repetida() -> None:
    publisher = FakePublisher()

    await ProcessExtraction(FakeDocumentStore(), FakeLlm(), publisher, FakePdf()).give_up(
        command(), "MinIO fora"
    )

    event_type, event = only_event(publisher)
    assert event_type == "extraction.failed"
    assert event["data"]["retryable"] is True


async def test_tarefa_com_llm_manda_o_documento_e_publica_a_saida() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"organ": "TJ-SP", "positions": []})

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, FakePdf()).execute(
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


BODY = "1 DAS DISPOSIÇÕES PRELIMINARES " + "regras do concurso " * 30
EDITAL_PAGES = [
    BODY,
    BODY + "\nQUADRO DE VAGAS Analista ...",
    BODY + "\nANEXO I\nCONTEÚDO PROGRAMÁTICO\n" + "tópico " * 60,
    "ANEXO I\nCONTEÚDO PROGRAMÁTICO\n" + "tópico " * 60,
    "ANEXO II\nATRIBUIÇÕES DOS CARGOS\n" + "atribuição " * 60,
    "ANEXO III\nCRONOGRAMA PREVISTO\n" + "data " * 60,
]


async def test_visao_geral_manda_so_as_paginas_que_usa_e_volta_as_paginas_do_original() -> None:
    publisher = FakePublisher()
    pdf = FakePdf(EDITAL_PAGES)
    llm = FakeLlm({"organ": "CREMERJ", "positions": [{"name": "Analista", "page": 2}, {"page": 4}]})

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, pdf).execute(
        command(task="EDITAL_OVERVIEW", params={})
    )

    # sem o conteúdo programático (p. 4) nem as atribuições (p. 5); a p. 3 tem corpo e Anexo I
    assert pdf.subsets == [[0, 1, 2, 5]]
    [(shas, *_)] = llm.calls
    assert shas == [hashlib.sha256(b"%PDF recorte 0,1,2,5").hexdigest()]
    _, event = only_event(publisher)
    # p. 4 do recorte é a p. 6 do edital
    assert event["data"]["result"]["positions"] == [
        {"name": "Analista", "page": 2},
        {"page": 6},
    ]


@pytest.mark.parametrize(
    "pdf",
    [
        FakePdf(["ANEXO I\nCONTEÚDO PROGRAMÁTICO"] * 3),  # anexo já na primeira página
        FakePdf([BODY, ""]),  # escaneado
        FakePdf(broken=True),
    ],
)
async def test_sem_achar_as_partes_manda_o_edital_inteiro(pdf: FakePdf) -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"positions": [{"page": 7}]})

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, pdf).execute(
        command(task="EDITAL_OVERVIEW", params={})
    )

    assert pdf.subsets == []
    [(shas, *_)] = llm.calls
    assert shas == [SHA]
    _, event = only_event(publisher)
    assert event["data"]["result"]["positions"] == [{"page": 7}]


async def test_conteudo_programatico_manda_o_anexo_e_o_cargo_nas_instrucoes() -> None:
    publisher = FakePublisher()
    pdf = FakePdf(EDITAL_PAGES)
    llm = FakeLlm({"position_found": True, "subjects": [{"name": "Português", "page": 2}]})
    position = {"name": "Analista", "specialty": "Desenvolvimento de Sistemas"}

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, pdf).execute(
        command(task="EDITAL_SYLLABUS", prompt_version="v2", params={"position": position})
    )

    # só as páginas do Anexo I (a 3 começa com o fim do corpo)
    assert pdf.subsets == [[2, 3]]
    [(_, instructions, output_model, effort)] = llm.calls
    assert output_model is EditalSyllabusV1
    assert effort == "medium"
    assert instructions.startswith(LLM_TASKS[Task.EDITAL_SYLLABUS]["v2"].instructions)
    assert '"specialty": "Desenvolvimento de Sistemas"' in instructions
    _, event = only_event(publisher)
    assert event["data"]["result"]["subjects"] == [{"name": "Português", "page": 4}]


@pytest.mark.parametrize("params", [{}, {"position": {"name": ""}}, {"cargo": "Analista"}])
async def test_conteudo_programatico_sem_cargo_valido_e_falha_definitiva(params: dict) -> None:
    publisher = FakePublisher()
    llm = FakeLlm()

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, FakePdf()).execute(
        command(task="EDITAL_SYLLABUS", params=params)
    )

    assert llm.calls == []
    event_type, event = only_event(publisher)
    assert event_type == "extraction.failed"
    assert "parâmetros inválidos" in event["data"]["reason"]
    assert event["data"]["retryable"] is False


GABARITO = b"%PDF-1.7 gabarito"
GABARITO_SHA = hashlib.sha256(GABARITO).hexdigest()
GABARITO_KEY = f"blobs/{GABARITO_SHA}.pdf"


async def test_prova_com_gabarito_separado_manda_os_dois_e_o_tipo() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"variant": "Tipo 1", "answer_key_found": True, "texts": [], "questions": []})
    store = FakeDocumentStore({KEY: PDF, GABARITO_KEY: GABARITO})

    await ProcessExtraction(store, llm, publisher, FakePdf()).execute(
        command(
            task="EXAM_PAPER",
            documents=[{"sha256": SHA, "key": KEY}, {"sha256": GABARITO_SHA, "key": GABARITO_KEY}],
            params={"variant": "Tipo 1"},
        )
    )

    [(shas, instructions, output_model, _)] = llm.calls
    # prova inteira (sem recorte) e o gabarito, na ordem
    assert shas == [SHA, GABARITO_SHA]
    assert output_model is ExamPaperV1
    assert '"variant": "Tipo 1"' in instructions
    event_type, _ = only_event(publisher)
    assert event_type == "extraction.completed"


async def test_prova_aceita_um_ou_dois_documentos() -> None:
    publisher = FakePublisher()
    three = [{"sha256": SHA, "key": KEY}] * 3

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), FakeLlm(), publisher, FakePdf()).execute(
        command(task="EXAM_PAPER", documents=three, params={})
    )

    _, event = only_event(publisher)
    assert "recebe 1 a 2 documento(s), vieram 3" in event["data"]["reason"]


async def test_topicos_das_questoes_vai_sem_documento() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"classifications": [{"ordinal": 0, "topic": "1.2"}]})
    params = {
        "exam_reading_id": "e1",
        "syllabus_reading_id": "s1",
        "topics": [{"code": "1.2", "subject": "Português", "text": "Crase."}],
        "questions": [
            {"ordinal": 0, "subject": "Português", "statement": "Assinale", "options": ["a", "b"]}
        ],
    }

    await ProcessExtraction(FakeDocumentStore(), llm, publisher, FakePdf()).execute(
        command(task="QUESTION_TOPICS", documents=[], params=params)
    )

    [(shas, instructions, output_model, _)] = llm.calls
    assert shas == []
    assert output_model is QuestionTopicsV1
    assert '"code": "1.2"' in instructions
    _, event = only_event(publisher)
    assert event["data"]["result"]["classifications"] == [{"ordinal": 0, "topic": "1.2"}]


async def test_questoes_do_topico_sem_documento_com_a_banca() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"questions": []})
    params = {
        "board": "FGV",
        "answer_format": "MULTIPLE_CHOICE",
        "options_count": 5,
        "subject": "Língua Portuguesa",
        "topic": "Crase.",
        "count": 5,
    }

    await ProcessExtraction(FakeDocumentStore(), llm, publisher, FakePdf()).execute(
        command(task="TOPIC_QUESTIONS", documents=[], params=params)
    )

    [(shas, instructions, output_model, effort)] = llm.calls
    assert shas == []
    assert output_model is TopicQuestionsV1
    assert effort == "high"
    assert '"board": "FGV"' in instructions


def question(number: int, page: int, *, answer: str | None = "A", statement: str = "Q") -> dict:
    return {
        "number": number,
        "subject": None,
        "kind": "MULTIPLE_CHOICE",
        "text_id": None,
        "statement": statement,
        "options": [{"letter": "A", "text": "a"}],
        "answer": answer,
        "annulled": False,
        "has_image": False,
        "page": page,
    }


async def test_prova_longa_e_lida_em_trechos_com_o_gabarito_do_fim() -> None:
    publisher = FakePublisher()
    pdf = FakePdf(["página"] * 40)
    chunk = {"variant": "Tipo 1", "answer_key_found": True, "texts": [], "questions": []}
    llm = FakeLlm(
        results=[
            chunk
            | {"questions": [question(1, 1), question(30, 16, answer=None, statement="Cort")]},
            chunk | {"questions": [question(30, 2, statement="Cortada, mas inteira aqui")]},
            chunk | {"questions": [question(80, 10)]},
        ]
    )

    await ProcessExtraction(FakeDocumentStore({KEY: PDF}), llm, publisher, pdf).execute(
        command(task="EXAM_PAPER", params={})
    )

    # 40 páginas: 1–16, 16–31, 31–40, cada trecho com as 2 últimas (o gabarito costuma estar lá)
    assert pdf.subsets == [
        list(range(0, 16)) + [38, 39],
        list(range(15, 31)) + [38, 39],
        list(range(30, 40)),
    ]
    assert len(llm.calls) == 3
    assert "páginas 16 a 31 de 40" in llm.calls[1][1]
    _, event = only_event(publisher)
    questions = event["data"]["result"]["questions"]
    # a 30 apareceu nos dois trechos: fica a inteira, com a página da prova toda
    assert [(q["number"], q["page"]) for q in questions] == [(1, 1), (30, 17), (80, 40)]
    assert questions[1]["statement"] == "Cortada, mas inteira aqui"
    assert event["data"]["usage"]["input_tokens"] == 3000


async def test_prova_curta_vai_inteira() -> None:
    publisher = FakePublisher()
    pdf = FakePdf(["página"] * 18)

    await ProcessExtraction(
        FakeDocumentStore({KEY: PDF}), FakeLlm({"questions": []}), publisher, pdf
    ).execute(command(task="EXAM_PAPER", params={}))

    assert pdf.subsets == []


async def test_resumo_do_topico_sem_documento() -> None:
    publisher = FakePublisher()
    llm = FakeLlm({"summary": "…", "key_points": [], "pitfalls": [], "laws": [], "readings": []})

    await ProcessExtraction(FakeDocumentStore(), llm, publisher, FakePdf()).execute(
        command(
            task="TOPIC_SUMMARY",
            documents=[],
            params={"subject": "Direito Administrativo", "topic": "Atos administrativos."},
        )
    )

    [(shas, instructions, output_model, _)] = llm.calls
    assert shas == []
    assert output_model is TopicSummaryV1
    assert '"topic": "Atos administrativos."' in instructions
