"""Fonte PCI Concursos via MCP público (https://mcp.pciconcursos.com.br/mcp).

`listar_concursos` sem filtro omite boa parte dos concursos de professor, que só vêm com
`professores=true`. Por isso a coleta completa faz as duas chamadas e junta pelo `id`.
"""

import asyncio
import logging
from collections.abc import Iterable
from datetime import date

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
from pydantic import BaseModel, ConfigDict, ValidationError, field_validator

from src.domain.entities.exam import Exam
from src.domain.entities.position import Position
from src.domain.errors import InvalidSourceDataError, SourceUnavailableError

logger = logging.getLogger(__name__)

SOURCE_NAME = "pciconcursos"
LISTING_CALLS: tuple[dict[str, object], ...] = ({}, {"professores": True})


# ---------- formato da resposta do PCI (só o que usamos; campos extras são ignorados) ----------


class _PciDates(BaseModel):
    inicio: date | None = None
    fim: date | None = None
    texto: str | None = None

    @field_validator("inicio", "fim", mode="before")
    @classmethod
    def _empty_is_none(cls, v: object) -> object:
        return v or None


class _PciNews(BaseModel):
    link: str


class _PciExam(BaseModel):
    id: int
    titulo: str
    cargos: list[str] = []
    vagas_salario: str | None = None
    formacao: str | None = None
    regiao: str | None = None
    uf: str | None = None
    datas: _PciDates = _PciDates()
    noticia: _PciNews


class _PciListing(BaseModel):
    model_config = ConfigDict(extra="ignore")

    # itens ficam crus: um concurso malformado não pode derrubar a coleta inteira
    data: list[dict[str, object]] = []
    errors: list[object] = []
    message: str | None = None


# ---------- parser (puro, testável só com o JSON) ----------


def parse_listing(raw: str) -> list[Exam]:
    try:
        listing = _PciListing.model_validate_json(raw)
    except ValidationError as e:
        raise InvalidSourceDataError(f"resposta do PCI fora do formato esperado: {e}") from e
    if listing.errors:
        logger.warning("PCI devolveu erros", extra={"errors": listing.errors})

    exams: list[Exam] = []
    for item in listing.data:
        try:
            exams.append(_to_exam(_PciExam.model_validate(item)))
        except ValidationError as e:
            logger.warning(
                "concurso ignorado: formato inválido",
                extra={"pci_id": item.get("id"), "error": str(e)},
            )
    return exams


def merge_listings(listings: Iterable[list[Exam]]) -> list[Exam]:
    """Junta as listagens sem repetir concurso (mesmo external_id)."""
    merged: dict[str, Exam] = {}
    for exams in listings:
        for exam in exams:
            merged.setdefault(exam.external_id, exam)
    return list(merged.values())


def _to_exam(item: _PciExam) -> Exam:
    # dict.fromkeys: tira cargo repetido mantendo a ordem da fonte
    names = dict.fromkeys(n.strip() for n in item.cargos if n.strip())
    return Exam(
        source=SOURCE_NAME,
        external_id=str(item.id),
        title=item.titulo,
        url=item.noticia.link,
        uf=item.uf or None,
        region=item.regiao or None,
        education_level=item.formacao or None,
        openings=item.vagas_salario or None,
        registration_start=item.datas.inicio,
        registration_end=item.datas.fim,
        registration_note=item.datas.texto or None,
        positions=tuple(Position(name=n) for n in names),
    )


# ---------- adapter (IO) ----------


class PciMcpSource:
    def __init__(self, url: str, timeout_s: float) -> None:
        self._url = url
        self._timeout_s = timeout_s

    @property
    def name(self) -> str:
        return SOURCE_NAME

    async def fetch_exams(self) -> list[Exam]:
        raws = await self._call_listings()
        # parse fora da sessão MCP: erro de formato não vira erro de conexão
        return merge_listings(parse_listing(raw) for raw in raws)

    async def _call_listings(self) -> list[str]:
        try:
            async with (
                asyncio.timeout(self._timeout_s),
                streamable_http_client(self._url) as streams,
                ClientSession(streams[0], streams[1]) as session,
            ):
                await session.initialize()
                return [await self._call(session, args) for args in LISTING_CALLS]
        except TimeoutError as e:
            raise SourceUnavailableError(f"PCI MCP não respondeu em {self._timeout_s}s") from e
        except SourceUnavailableError:
            raise
        except Exception as e:  # rede, protocolo MCP, TaskGroup do anyio (ExceptionGroup)
            raise SourceUnavailableError(f"falha ao chamar o PCI MCP: {e!r}") from e

    @staticmethod
    async def _call(session: ClientSession, args: dict[str, object]) -> str:
        result = await session.call_tool("listar_concursos", args)
        text = "".join(getattr(block, "text", "") for block in result.content)
        if result.is_error:
            raise SourceUnavailableError(f"listar_concursos{args} falhou: {text[:200]}")
        return text
