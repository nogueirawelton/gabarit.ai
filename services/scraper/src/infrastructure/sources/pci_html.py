"""Fonte PCI Concursos pelas páginas públicas (HTML), em duas etapas.

1. Catálogo de cargos: /cargos/ e /vagas/ (as listas se completam; a união é o catálogo).
2. Para cada cargo do catálogo:
   - /cargos/<slug>: concursos abertos com o id do PCI e os nomes crus dos cargos que batem;
   - /vagas/<slug>: cards com UF, inscrições, vagas e escolaridade (sem id). Só os abertos
     interessam: div.na e div.da; div.ea são encerrados.
   As duas se ligam pelo link da notícia. Um concurso aparece em várias páginas de cargo:
   no fim, agrupa pelo id e junta os cargos.

O site não tem captcha nessas páginas e o robots.txt permite. Uma requisição por vez, com
pausa entre elas, User-Agent identificado e respeito a Retry-After.
"""

import asyncio
import logging
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field
from datetime import date
from urllib.parse import urlparse

import httpx
from pydantic import ValidationError
from selectolax.lexbor import LexborHTMLParser, LexborNode

from src.domain.entities.catalog_position import CatalogPosition
from src.domain.entities.exam import Exam
from src.domain.entities.position import Position
from src.domain.errors import InvalidSourceDataError, SourceUnavailableError
from src.domain.ports.exam_source import SourceCollection

logger = logging.getLogger(__name__)

SOURCE_NAME = "pciconcursos"

_UF_REGION = {
    **dict.fromkeys(("AC", "AP", "AM", "PA", "RO", "RR", "TO"), "NORTE"),
    **dict.fromkeys(("AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE"), "NORDESTE"),
    **dict.fromkeys(("DF", "GO", "MT", "MS"), "CENTRO-OESTE"),
    **dict.fromkeys(("ES", "MG", "RJ", "SP"), "SUDESTE"),
    **dict.fromkeys(("PR", "RS", "SC"), "SUL"),
}
_ACTIVE_GROUPS = ("na", "da")
_SLUG = r"([a-z0-9]+(?:-[a-z0-9]+)*)"


# ---------- estruturas intermediárias (só deste módulo) ----------


@dataclass(frozen=True)
class CargoHit:
    """Um concurso na página /cargos/<slug>."""

    pci_id: str
    title: str
    url: str
    names: tuple[str, ...]


@dataclass(frozen=True)
class CardDetails:
    """Um card aberto da página /vagas/<slug>."""

    uf: str | None
    education_level: str | None
    openings: str | None
    registration_start: date | None
    registration_end: date | None
    registration_note: str | None


@dataclass
class _Draft:
    title: str
    url: str
    # nome cru → slugs do catálogo em que apareceu (dict: mantém a ordem da fonte)
    names: dict[str, set[str]] = field(default_factory=dict)


# ---------- parsers (puros, testáveis só com o HTML) ----------


def _slug_links(html: str, section: str) -> list[tuple[str, str]]:
    tree = LexborHTMLParser(html)
    found: list[tuple[str, str]] = []
    for a in tree.css("ul.link-i a"):
        path = urlparse(a.attributes.get("href") or "").path
        m = re.fullmatch(rf"/{section}/{_SLUG}", path)
        if m:
            found.append((m.group(1), a.text().strip()))
    return found


def parse_cargos_index(html: str) -> list[tuple[str, str]]:
    """/cargos/ → [(slug, nome)]. Nome em caixa alta, como o site escreve."""
    return _slug_links(html, "cargos")


def parse_vagas_index(html: str) -> list[tuple[str, str]]:
    """/vagas/ → [(slug, nome)], sem o "(N concursos)" do fim."""
    return [
        (slug, re.sub(r"\s*\(\d+ concursos?\)\s*$", "", name))
        for slug, name in _slug_links(html, "vagas")
    ]


def merge_catalog(
    cargos: Iterable[tuple[str, str]], vagas: Iterable[tuple[str, str]]
) -> list[CatalogPosition]:
    """União das duas listas. O nome da /vagas/ ("Enfermeiro") ganha do da /cargos/ (caixa alta)."""
    names: dict[str, str] = {}
    for slug, name in cargos:
        names.setdefault(slug, name)
    for slug, name in vagas:
        names[slug] = name
    return [
        CatalogPosition(source=SOURCE_NAME, slug=slug, name=name)
        for slug, name in sorted(names.items())
        if name
    ]


def _next_element(node: LexborNode) -> LexborNode | None:
    nxt = node.next
    while nxt is not None and nxt.tag == "-text":
        nxt = nxt.next
    return nxt


def parse_cargo_page(html: str) -> list[CargoHit]:
    """/cargos/<slug> → concursos abertos. Cada um é um ul.link-d (notícia, com o id do PCI na
    classe "nNNNNNN") seguido de um ul.link-i com os cargos crus que batem com o slug."""
    tree = LexborHTMLParser(html)
    hits: list[CargoHit] = []
    for ul in tree.css("ul.link-d"):
        a = ul.css_first("a.noticia_desc")
        if a is None:
            continue  # ul.link-d vazio: separador
        m = re.search(r"\bn(\d+)\b", a.attributes.get("class") or "")
        url = a.attributes.get("href") or ""
        title = a.text().strip()
        if not m or not url or not title:
            logger.warning(
                "concurso ignorado: item sem id/link/título", extra={"html": ul.html[:200]}
            )
            continue
        names: tuple[str, ...] = ()
        nxt = _next_element(ul)
        if nxt is not None and nxt.tag == "ul" and "link-i" in (nxt.attributes.get("class") or ""):
            names = tuple(n for x in nxt.css("a") if (n := x.text().strip()))
        hits.append(CargoHit(pci_id=m.group(1), title=title, url=url, names=names))
    return hits


def parse_registration(text: str) -> tuple[date | None, date | None, str | None]:
    """Texto de inscrições do card → (início, fim, nota). Formatos vistos no site:
    "23/11/2026", "29/10 a 23/11/2026", "29/10/2026 a 23/11/2026", "04 a 23/11/2026",
    "Prorrogado até 23/11/2026", "Reaberto até 23/11/2026". Fora disso: só a nota, com o texto.
    """
    text = " ".join(text.split())
    if not text:
        return None, None, None
    try:
        if m := re.fullmatch(r"(\d{2})/(\d{2})/(\d{4})", text):
            return None, _date(m, 1), None
        if m := re.fullmatch(r"(\d{2})/(\d{2})/(\d{4}) a (\d{2})/(\d{2})/(\d{4})", text):
            return _ordered(_date(m, 1), _date(m, 4))
        if m := re.fullmatch(r"(\d{2})/(\d{2}) a (\d{2})/(\d{2})/(\d{4})", text):
            end = _date(m, 3)
            start = date(end.year, int(m.group(2)), int(m.group(1)))
            if start > end:  # "20/12 a 10/01/2027": começa no ano anterior
                start = start.replace(year=end.year - 1)
            return _ordered(start, end)
        if m := re.fullmatch(r"(\d{2}) a (\d{2})/(\d{2})/(\d{4})", text):
            end = _date(m, 2)
            day = int(m.group(1))
            if day <= end.day:
                return _ordered(end.replace(day=day), end)
            # "28 a 05/11/2026": começa no mês anterior
            prev_month = end.replace(day=1).toordinal() - 1
            return _ordered(date.fromordinal(prev_month).replace(day=day), end)
        if m := re.fullmatch(r"(.+?) até (\d{2})/(\d{2})/(\d{4})", text):
            return None, _date(m, 2), m.group(1)
    except ValueError:
        pass  # data impossível ("31/02/2026"): cai no texto cru
    return None, None, text


def _date(m: re.Match[str], first_group: int) -> date:
    day, month, year = (int(m.group(first_group + i)) for i in range(3))
    return date(year, month, day)


def _ordered(start: date, end: date) -> tuple[date | None, date | None, None]:
    # fim antes do início é erro da fonte: fica só o fim (o Exam recusaria o par)
    return (start if start <= end else None), end, None


def parse_vagas_page(html: str) -> dict[str, CardDetails]:
    """/vagas/<slug> → {link da notícia: detalhes} dos cards abertos (div.na e div.da).

    Algumas páginas vêm quebradas do site (título "Concursos com vagas para " sem o cargo, e uma
    listagem genérica): essas não servem e devolvem {}.
    """
    tree = LexborHTMLParser(html)
    title = tree.css_first("title")
    if title is None or re.fullmatch(r"\s*Concursos com vagas para\s*", title.text()):
        logger.warning("página de vagas quebrada no site, ignorada")
        return {}

    details: dict[str, CardDetails] = {}
    for card in tree.css("div.ca"):
        if card.parent is None or card.parent.attributes.get("class") not in _ACTIVE_GROUPS:
            continue
        a = card.css_first("a")
        url = a.attributes.get("href") if a is not None else None
        if not url:
            continue
        details.setdefault(url, _card_details(card))
    return details


def _card_details(card: LexborNode) -> CardDetails:
    uf_node = card.css_first("div.cc")
    uf = uf_node.text().strip().upper() if uf_node is not None else ""

    # div.cd: "<vagas e salário><br><span><cargos resumidos><br><span><escolaridade></span></span>"
    cd = card.css_first("div.cd")
    openings = cd.text(deep=False).strip() if cd is not None else ""
    spans = cd.css("span") if cd is not None else []
    education = spans[-1].text(deep=False).strip() if spans else ""

    ce = card.css_first("div.ce")
    start, end, note = parse_registration(ce.text(separator=" ") if ce is not None else "")
    return CardDetails(
        uf=uf if uf in _UF_REGION else None,
        education_level=education or None,
        openings=openings or None,
        registration_start=start,
        registration_end=end,
        registration_note=note,
    )


def build_exams(
    hits_by_slug: Mapping[str, Iterable[CargoHit]], details: Mapping[str, CardDetails]
) -> list[Exam]:
    """Agrupa por id do PCI: um concurso aparece em várias páginas de cargo."""
    drafts: dict[str, _Draft] = {}
    for slug, hits in hits_by_slug.items():
        for hit in hits:
            draft = drafts.setdefault(hit.pci_id, _Draft(title=hit.title, url=hit.url))
            for name in hit.names:
                draft.names.setdefault(name, set()).add(slug)

    exams: list[Exam] = []
    for pci_id, draft in drafts.items():
        d = details.get(draft.url)
        try:
            exams.append(
                Exam(
                    source=SOURCE_NAME,
                    external_id=pci_id,
                    title=draft.title,
                    url=draft.url,
                    uf=d.uf if d else None,
                    region=_UF_REGION.get(d.uf) if d and d.uf else None,
                    education_level=d.education_level if d else None,
                    openings=d.openings if d else None,
                    registration_start=d.registration_start if d else None,
                    registration_end=d.registration_end if d else None,
                    registration_note=d.registration_note if d else None,
                    positions=tuple(
                        Position(name=name, catalog_slugs=tuple(sorted(slugs)))
                        for name, slugs in draft.names.items()
                    ),
                )
            )
        except ValidationError as e:
            logger.warning(
                "concurso ignorado: formato inválido", extra={"pci_id": pci_id, "error": str(e)}
            )
    return exams


# ---------- adapter (IO) ----------


class PciHtmlSource:
    def __init__(
        self,
        base_url: str,
        *,
        user_agent: str,
        timeout_s: float,
        delay_s: float,
        max_retries: int,
        backoff_s: float = 2.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url
        self._user_agent = user_agent
        self._timeout_s = timeout_s
        self._delay_s = delay_s
        self._max_retries = max_retries
        self._backoff_s = backoff_s
        # só para testes (httpx.MockTransport)
        self._transport = transport

    @property
    def name(self) -> str:
        return SOURCE_NAME

    async def collect(self) -> SourceCollection:
        async with httpx.AsyncClient(
            base_url=self._base_url,
            headers={"User-Agent": self._user_agent},
            timeout=self._timeout_s,
            follow_redirects=True,
            transport=self._transport,
        ) as client:
            fetch = _Fetcher(client, self._delay_s, self._max_retries, self._backoff_s)

            cargos_index = await fetch.text("/cargos/")
            vagas_index = await fetch.text("/vagas/")
            if cargos_index is None or vagas_index is None:
                raise InvalidSourceDataError("índice de cargos do PCI não encontrado (404)")
            catalog = merge_catalog(
                parse_cargos_index(cargos_index), parse_vagas_index(vagas_index)
            )
            if not catalog:
                raise InvalidSourceDataError("catálogo de cargos do PCI vazio: o HTML mudou?")
            logger.info("catálogo coletado", extra={"source": SOURCE_NAME, "count": len(catalog)})

            hits_by_slug: dict[str, list[CargoHit]] = {}
            details: dict[str, CardDetails] = {}
            missing = 0
            for i, entry in enumerate(catalog, start=1):
                cargo_page = await fetch.text(f"/cargos/{entry.slug}")
                if cargo_page is not None:
                    hits_by_slug[entry.slug] = parse_cargo_page(cargo_page)
                vagas_page = await fetch.text(f"/vagas/{entry.slug}")
                if vagas_page is not None:
                    for url, card in parse_vagas_page(vagas_page).items():
                        details.setdefault(url, card)
                missing += (cargo_page is None) + (vagas_page is None)
                if i % 50 == 0:
                    logger.info("coletando cargos", extra={"done": i, "total": len(catalog)})

        exams = build_exams(hits_by_slug, details)
        if not exams:
            raise InvalidSourceDataError("nenhum concurso nas páginas de cargo: o HTML mudou?")
        logger.info(
            "concursos montados",
            extra={
                "source": SOURCE_NAME,
                "count": len(exams),
                "without_details": sum(1 for e in exams if e.registration_end is None),
                "pages_404": missing,
            },
        )
        return SourceCollection(exams=tuple(exams), catalog=tuple(catalog))


class _Fetcher:
    """GET com pausa fixa entre requisições e novas tentativas em erro transitório."""

    def __init__(
        self, client: httpx.AsyncClient, delay_s: float, max_retries: int, backoff_s: float
    ) -> None:
        self._client = client
        self._delay_s = delay_s
        self._max_retries = max_retries
        self._backoff_s = backoff_s
        self._last: float | None = None

    async def text(self, path: str) -> str | None:
        """Corpo da página, ou None se 404. Esgotou as tentativas → SourceUnavailableError."""
        reason = ""
        for attempt in range(self._max_retries + 1):
            await self._throttle()
            wait = self._backoff_s * 2**attempt
            try:
                response = await self._client.get(path)
            except httpx.TransportError as e:
                reason = repr(e)
            else:
                if response.status_code == 404:
                    return None
                if response.is_success:
                    return response.text
                if response.status_code != 429 and response.status_code < 500:
                    raise SourceUnavailableError(f"PCI {path}: HTTP {response.status_code}")
                reason = f"HTTP {response.status_code}"
                wait = max(wait, _retry_after(response))
            if attempt < self._max_retries:
                logger.warning(
                    "PCI falhou, tentando de novo",
                    extra={"path": path, "reason": reason, "attempt": attempt + 1, "wait_s": wait},
                )
                await asyncio.sleep(min(wait, 60))
        attempts = self._max_retries + 1
        raise SourceUnavailableError(f"PCI {path}: {reason} após {attempts} tentativas")

    async def _throttle(self) -> None:
        loop = asyncio.get_running_loop()
        if self._last is not None:
            remaining = self._delay_s - (loop.time() - self._last)
            if remaining > 0:
                await asyncio.sleep(remaining)
        self._last = loop.time()


def _retry_after(response: httpx.Response) -> float:
    value = response.headers.get("Retry-After", "")
    return float(value) if value.isdigit() else 0.0
