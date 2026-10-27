import json
from datetime import date

import pytest

from src.application.messages import ScrapeRequested
from src.application.use_cases.scrape_exam import ScrapeExams
from src.domain.entities.catalog_position import CatalogPosition
from src.domain.entities.exam import Exam
from src.domain.entities.position import Position
from src.domain.errors import SourceUnavailableError, UnknownSourceError
from tests.fakes import FakeExamSource, FakePublisher


def make_exam(external_id: str = "1", *names: str) -> Exam:
    return Exam(
        source="fake",
        external_id=external_id,
        title="Prefeitura X",
        url=f"https://example.com/{external_id}",
        uf="SP",
        registration_start=date(2026, 10, 1),
        registration_end=date(2026, 11, 1),
        positions=tuple(Position(name=n) for n in names or ("ANALISTA",)),
    )


async def test_publica_um_evento_por_concurso_com_os_cargos() -> None:
    exams = [make_exam("1", "ANALISTA", "TÉCNICO"), make_exam("2", "PROFESSOR")]
    publisher = FakePublisher()
    use_case = ScrapeExams({"fake": FakeExamSource(exams)}, publisher)

    count = await use_case.execute(ScrapeRequested(source="fake"), correlation_id="cmd-1")

    assert count == 2
    assert [t for _, t, _ in publisher.published] == ["exam.discovered"] * 2
    first = json.loads(publisher.published[0][2])
    assert first["correlation_id"] == "cmd-1"
    assert first["data"]["external_id"] == "1"
    assert [p["name"] for p in first["data"]["positions"]] == ["ANALISTA", "TÉCNICO"]
    # event_id do envelope = message_id usado na publicação
    assert first["event_id"] == str(publisher.published[0][0])


async def test_catalogo_sai_antes_dos_concursos_num_evento_so() -> None:
    catalog = (
        CatalogPosition(source="fake", slug="analista", name="Analista"),
        CatalogPosition(source="fake", slug="tecnico", name="Técnico"),
    )
    publisher = FakePublisher()
    use_case = ScrapeExams({"fake": FakeExamSource([make_exam("1")], catalog=catalog)}, publisher)

    await use_case.execute(ScrapeRequested(source="fake"), correlation_id="cmd-1")

    assert [t for _, t, _ in publisher.published] == [
        "position_catalog.discovered",
        "exam.discovered",
    ]
    event = json.loads(publisher.published[0][2])
    assert event["correlation_id"] == "cmd-1"
    assert event["data"] == {
        "source": "fake",
        "positions": [
            {"slug": "analista", "name": "Analista"},
            {"slug": "tecnico", "name": "Técnico"},
        ],
    }


async def test_fonte_desconhecida_nao_publica_nada() -> None:
    publisher = FakePublisher()
    use_case = ScrapeExams({"fake": FakeExamSource([make_exam()])}, publisher)

    with pytest.raises(UnknownSourceError):
        await use_case.execute(ScrapeRequested(source="outra"))
    assert publisher.published == []


async def test_falha_da_fonte_nao_publica_nada() -> None:
    publisher = FakePublisher()
    use_case = ScrapeExams({"fake": FakeExamSource([], fail=True)}, publisher)

    with pytest.raises(SourceUnavailableError):
        await use_case.execute(ScrapeRequested(source="fake"))
    assert publisher.published == []


async def test_coletar_de_novo_mantem_a_identidade_do_concurso() -> None:
    publisher = FakePublisher()
    source = FakeExamSource([make_exam("42")])
    use_case = ScrapeExams({"fake": source}, publisher)

    await use_case.execute(ScrapeRequested(source="fake"))
    await use_case.execute(ScrapeRequested(source="fake"))

    events = [json.loads(body) for _, _, body in publisher.published]
    # eventos distintos, mesmo concurso: o `core` faz upsert por (source, external_id)
    assert events[0]["event_id"] != events[1]["event_id"]
    assert {(e["data"]["source"], e["data"]["external_id"]) for e in events} == {("fake", "42")}
