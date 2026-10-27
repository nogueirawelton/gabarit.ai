import json
from datetime import date
from pathlib import Path

import pytest

from src.domain.errors import InvalidSourceDataError
from src.infrastructure.sources.pci_mcp import merge_listings, parse_listing

FIXTURES = Path(__file__).parent.parent / "fixtures" / "pci"


def fixture(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def test_mapeia_concurso_e_cargos() -> None:
    exams = {e.external_id: e for e in parse_listing(fixture("listar_concursos.json"))}

    exam = exams["295975"]
    assert exam.source == "pciconcursos"
    assert exam.uf == "SP"
    assert exam.registration_start == date(2026, 10, 28)
    assert exam.registration_end == date(2026, 12, 10)
    assert exam.url.startswith("https://www.pciconcursos.com.br/noticias/")
    assert [p.name for p in exam.positions] == [
        "ESCREVENTE TÉCNICO JUDICIÁRIO",
        "OFICIAL DE JUSTIÇA",
    ]


def test_concurso_nacional_tem_uf_none() -> None:
    exams = {e.external_id: e for e in parse_listing(fixture("listar_concursos.json"))}
    assert exams["295622"].uf is None
    assert exams["295622"].region == "NACIONAL"


def test_concurso_sem_datas_guarda_a_situacao() -> None:
    exams = {e.external_id: e for e in parse_listing(fixture("listar_concursos.json"))}
    suspenso = exams["294131"]
    assert suspenso.registration_start is None
    assert suspenso.registration_end is None
    assert suspenso.registration_note == "Suspenso"


def test_cargo_repetido_ou_vazio_e_descartado() -> None:
    raw = json.loads(fixture("listar_concursos.json"))
    raw["data"][0]["cargos"] = ["ANALISTA", " ANALISTA ", "", "TÉCNICO"]

    exam = parse_listing(json.dumps(raw))[0]

    assert [p.name for p in exam.positions] == ["ANALISTA", "TÉCNICO"]


def test_concurso_malformado_e_ignorado_sem_derrubar_os_outros() -> None:
    raw = json.loads(fixture("listar_concursos.json"))
    del raw["data"][0]["noticia"]

    exams = parse_listing(json.dumps(raw))

    assert len(exams) == len(raw["data"]) - 1


def test_resposta_vazia_com_message() -> None:
    raw = {"meta": {"total": 0}, "data": [], "errors": [], "message": "Nenhum concurso aberto"}
    assert parse_listing(json.dumps(raw)) == []


def test_resposta_fora_do_formato_levanta_erro_de_dados() -> None:
    with pytest.raises(InvalidSourceDataError):
        parse_listing("isso não é json")


def test_junta_listagens_sem_repetir_concurso() -> None:
    geral = parse_listing(fixture("listar_concursos.json"))
    professores = parse_listing(fixture("listar_concursos_professores.json"))

    merged = merge_listings([geral, professores])

    ids = [e.external_id for e in merged]
    assert len(ids) == len(set(ids))
    # 295398 está nas duas; 295450 só vem com professores=true
    assert {"295398", "295450"} <= set(ids)
    assert len(merged) == len(geral) + len(professores) - 1
