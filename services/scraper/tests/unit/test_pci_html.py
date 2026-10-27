from datetime import date
from pathlib import Path

import httpx
import pytest

from src.domain.errors import InvalidSourceDataError, SourceUnavailableError
from src.infrastructure.sources.pci_html import (
    CardDetails,
    CargoHit,
    PciHtmlSource,
    build_exams,
    merge_catalog,
    parse_cargo_page,
    parse_cargos_index,
    parse_registration,
    parse_vagas_index,
    parse_vagas_page,
)

FIXTURES = Path(__file__).parent.parent / "fixtures" / "pci_html"


def fixture(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


# ---------- catálogo ----------


def test_catalogo_e_a_uniao_das_duas_listas() -> None:
    cargos = parse_cargos_index(fixture("cargos_index.html"))
    vagas = parse_vagas_index(fixture("vagas_index.html"))

    catalog = {c.slug: c.name for c in merge_catalog(cargos, vagas)}

    assert (len(cargos), len(vagas), len(catalog)) == (223, 200, 248)
    # nas duas: o nome da /vagas/ ganha, e sem o "(N concursos)"
    assert catalog["enfermeiro"] == "Enfermeiro"
    # só na /vagas/
    assert catalog["tecnico-em-contabilidade"] == "Técnico em Contabilidade"
    # só na /cargos/: nome em caixa alta, como o site escreve
    assert catalog["agente-de-endemias"] == "AGENTE DE ENDEMIAS"


# ---------- /cargos/<slug> ----------


def test_pagina_de_cargo_traz_id_titulo_link_e_nomes_crus() -> None:
    hits = {h.pci_id: h for h in parse_cargo_page(fixture("cargos_enfermeiro.html"))}

    assert len(hits) == 114
    inb = hits["295850"]
    assert inb.title == "INB - Indústrias Nucleares do Brasil S.A."
    assert inb.url.startswith("https://www.pciconcursos.com.br/noticias/inb-abre-concurso")
    assert inb.names == ("ENFERMEIRO DO TRABALHO",)


# ---------- /vagas/<slug> ----------


def test_pagina_de_vagas_traz_so_os_abertos_com_detalhes() -> None:
    details = parse_vagas_page(fixture("vagas_enfermeiro.html"))

    # 114 abertos (div.na + div.da); os 86 encerrados (div.ea) ficam de fora
    assert len(details) == 114
    inb = details[
        "https://www.pciconcursos.com.br/noticias/"
        "inb-abre-concurso-publico-para-cadastro-de-reserva-em-diversas-areas-de-atuacao"
    ]
    assert inb == CardDetails(
        uf=None,
        education_level="Médio / Técnico / Superior",
        openings="Cadastro de reserva até R$ 8.374,00",
        registration_start=None,
        registration_end=date(2026, 11, 11),
        registration_note=None,
    )
    assert sum(1 for d in details.values() if d.registration_note == "Prorrogado") == 3


def test_pagina_de_vagas_quebrada_no_site_e_ignorada() -> None:
    # /vagas/tecnico-em-contabilidade: o site devolve título sem o cargo e uma listagem genérica
    assert parse_vagas_page(fixture("vagas_quebrada.html")) == {}


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("23/11/2026", (None, date(2026, 11, 23), None)),
        ("29/10 a 23/11/2026", (date(2026, 10, 29), date(2026, 11, 23), None)),
        ("29/10/2026 a 23/11/2026", (date(2026, 10, 29), date(2026, 11, 23), None)),
        ("04 a 23/11/2026", (date(2026, 11, 4), date(2026, 11, 23), None)),
        ("Prorrogado até 23/11/2026", (None, date(2026, 11, 23), "Prorrogado")),
        ("Reaberto até\n 23/11/2026", (None, date(2026, 11, 23), "Reaberto")),
        # virada de ano e de mês
        ("20/12 a 10/01/2027", (date(2026, 12, 20), date(2027, 1, 10), None)),
        ("28 a 05/11/2026", (date(2026, 10, 28), date(2026, 11, 5), None)),
        # fim antes do início: fica só o fim
        ("30/11/2026 a 23/11/2026", (None, date(2026, 11, 23), None)),
        # formato desconhecido ou data impossível: texto cru na nota
        ("Suspenso", (None, None, "Suspenso")),
        ("31/02/2026", (None, None, "31/02/2026")),
        ("", (None, None, None)),
    ],
)
def test_texto_das_inscricoes(
    text: str, expected: tuple[date | None, date | None, str | None]
) -> None:
    assert parse_registration(text) == expected


# ---------- montagem ----------


def details(uf: str | None = "SP") -> CardDetails:
    return CardDetails(uf, "Superior", "10 vagas", date(2026, 11, 1), date(2026, 11, 30), None)


def test_concurso_em_varias_paginas_vira_um_so_com_os_cargos_juntos() -> None:
    url = "https://www.pciconcursos.com.br/noticias/x"
    exams = build_exams(
        {
            "enfermeiro": [CargoHit("1", "Prefeitura X", url, ("ENFERMEIRO", "ENFERMEIRO PSF"))],
            "enfermeiro-psf": [CargoHit("1", "Prefeitura X", url, ("ENFERMEIRO PSF",))],
            "medico": [CargoHit("1", "Prefeitura X", url, ("MÉDICO",))],
        },
        {url: details()},
    )

    assert len(exams) == 1
    exam = exams[0]
    assert (exam.external_id, exam.uf, exam.region) == ("1", "SP", "SUDESTE")
    assert [(p.name, p.catalog_slugs) for p in exam.positions] == [
        ("ENFERMEIRO", ("enfermeiro",)),
        ("ENFERMEIRO PSF", ("enfermeiro", "enfermeiro-psf")),
        ("MÉDICO", ("medico",)),
    ]


def test_concurso_sem_card_de_vagas_entra_sem_detalhes() -> None:
    exams = build_exams({"x": [CargoHit("1", "Órgão", "https://example.com/1", ("CARGO",))]}, {})

    assert exams[0].registration_end is None
    assert exams[0].uf is None


def test_concurso_nacional_fica_sem_uf_e_regiao() -> None:
    url = "https://example.com/1"
    exams = build_exams({"x": [CargoHit("1", "Órgão", url, ("CARGO",))]}, {url: details(uf=None)})

    assert (exams[0].uf, exams[0].region) == (None, None)


def test_pagina_real_vira_concursos() -> None:
    exams = build_exams(
        {"enfermeiro": parse_cargo_page(fixture("cargos_enfermeiro.html"))},
        parse_vagas_page(fixture("vagas_enfermeiro.html")),
    )

    assert len(exams) == 114
    assert all(e.registration_end is not None for e in exams)
    # 295343 aparece duas vezes na página, com cargos diferentes
    repeated = next(e for e in exams if e.external_id == "295343")
    assert [p.name for p in repeated.positions] == ["ENFERMEIRO", "ENFERMEIRO - CENTRO CIRÚRGICO"]


# ---------- adapter (HTTP falso, sem rede) ----------

MINI_CARGOS = (
    '<ul class="link-i"><li><a href="https://www.pciconcursos.com.br/cargos/enfermeiro">'
    "ENFERMEIRO</a></li></ul>"
)
MINI_VAGAS = (
    '<ul class="link-i"><li><a href="https://www.pciconcursos.com.br/vagas/enfermeiro">'
    "Enfermeiro (114 concursos)</a></li></ul>"
)


def source(handler) -> PciHtmlSource:
    return PciHtmlSource(
        "https://www.pciconcursos.com.br",
        user_agent="teste",
        timeout_s=5,
        delay_s=0,
        max_retries=2,
        backoff_s=0,
        transport=httpx.MockTransport(handler),
    )


def site(overrides: dict[str, httpx.Response] | None = None):
    pages = {
        "/cargos/": MINI_CARGOS,
        "/vagas/": MINI_VAGAS,
        "/cargos/enfermeiro": fixture("cargos_enfermeiro.html"),
        "/vagas/enfermeiro": fixture("vagas_enfermeiro.html"),
    }
    calls: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request.url.path)
        if overrides and request.url.path in overrides:
            return overrides[request.url.path]
        body = pages.get(request.url.path)
        return httpx.Response(200, text=body) if body is not None else httpx.Response(404)

    return handler, calls


async def test_coleta_completa_pelas_duas_etapas() -> None:
    handler, calls = site()

    collection = await source(handler).collect()

    assert calls == ["/cargos/", "/vagas/", "/cargos/enfermeiro", "/vagas/enfermeiro"]
    assert [(c.slug, c.name) for c in collection.catalog] == [("enfermeiro", "Enfermeiro")]
    assert len(collection.exams) == 114


async def test_tenta_de_novo_em_erro_transitorio() -> None:
    attempts = {"n": 0}
    handler, _ = site()

    def flaky(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/vagas/enfermeiro" and attempts["n"] < 2:
            attempts["n"] += 1
            return httpx.Response(503)
        return handler(request)

    collection = await source(flaky).collect()

    assert attempts["n"] == 2
    assert len(collection.exams) == 114


async def test_fonte_fora_do_ar_vira_erro_transitorio() -> None:
    handler, _ = site({"/cargos/enfermeiro": httpx.Response(503)})

    with pytest.raises(SourceUnavailableError):
        await source(handler).collect()


async def test_pagina_de_cargo_que_nao_existe_e_pulada() -> None:
    handler, _ = site({"/vagas/enfermeiro": httpx.Response(404)})

    collection = await source(handler).collect()

    # sem /vagas/: os concursos entram, só que sem detalhes
    assert len(collection.exams) == 114
    assert all(e.registration_end is None for e in collection.exams)


async def test_catalogo_vazio_e_erro_de_formato() -> None:
    handler, _ = site(
        {
            "/cargos/": httpx.Response(200, text="<html></html>"),
            "/vagas/": httpx.Response(200, text="<html></html>"),
        }
    )

    with pytest.raises(InvalidSourceDataError):
        await source(handler).collect()
