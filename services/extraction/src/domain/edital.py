"""Partes de um edital, para mandar à LLM só as páginas que a tarefa precisa.

O edital costuma ter o corpo (regras, quadro de vagas, datas) e anexos: conteúdo programático e
atribuições dos cargos são quase metade das páginas e só servem a algumas tarefas. As partes são
achadas pelos títulos dos anexos ("ANEXO II" + título na mesma linha ou nas seguintes).

Na dúvida, devolve None (= manda o PDF inteiro): recortar é economia, nunca motivo de leitura
errada. Por isso tudo que não for claramente conteúdo programático ou atribuições fica como
OTHER_ANNEX.
"""

import re
import unicodedata
from collections.abc import Collection, Sequence
from enum import StrEnum


class EditalPart(StrEnum):
    BODY = "BODY"
    SYLLABUS = "SYLLABUS"
    DUTIES = "DUTIES"
    OTHER_ANNEX = "OTHER_ANNEX"


# "ANEXO II", "ANEXO 3 – CRONOGRAMA", "ANEXO ÚNICO: ..."; em maiúsculas e no começo da linha, para
# não pegar menções no texto ("conforme Anexo II")
_HEADING = re.compile(
    r"^ANEXO\s+(?P<number>[IVXL]+|\d{1,2}|ÚNICO|UNICO)\b\s*(?:[-–—:.]\s*(?P<title>.*))?$"
)
# linha de sumário: "ANEXO I – CONTEÚDO PROGRAMÁTICO ........ 31"
_TOC = re.compile(r"\.{4,}\s*\d+")
_ROMAN = {"I": 1, "V": 5, "X": 10, "L": 50}

# título sem acento, maiúsculo e sem espaços (o texto do PDF às vezes quebra palavras: "docume nto")
_SYLLABUS = ("CONTEUDOPROGRAMATICO", "CONTEUDOSPROGRAMATICOS", "OBJETOSDEAVALIACAO", "PROGRAMADE")
_DUTIES = ("ATRIBUICOES", "ATRIBUICAO")
# anexo que traz vagas, salário ou datas fica, mesmo que também fale de atribuições
_KEEP = ("VAGAS", "REMUNERA", "SALARI", "VENCIMENTO", "CRONOGRAMA", "CALENDARIO")

# página com menos texto que isso não tem camada de texto (escaneada)
_MIN_PAGE_TEXT = 50
# acima desta fração de páginas sem texto, não dá para achar as partes
_MAX_EMPTY_PAGES = 0.2
# texto antes do título do anexo na mesma página que ainda conta como a parte anterior
# (cabeçalho e rodapé de página ficam abaixo disso)
_MIN_TAIL_TEXT = 200
# dois títulos de anexo com menos texto que isso entre eles são uma lista ou sumário, não anexos
_MIN_ANNEX_TEXT = 300


def edital_parts(pages: Sequence[str]) -> list[frozenset[EditalPart]] | None:
    """As partes presentes em cada página (texto de cada página, na ordem), ou None se não der
    para saber com segurança (PDF escaneado, anexos fora de ordem, sumário, nenhum anexo)."""
    empty = sum(1 for text in pages if len(text.strip()) < _MIN_PAGE_TEXT)
    if not pages or empty > len(pages) * _MAX_EMPTY_PAGES:
        return None

    try:
        headings = [_headings(text) for text in pages]
    except _Unsure:
        return None
    found = [(page, *heading) for page, items in enumerate(headings) for heading in items]
    if not found or found[0][0] == 0:
        # sem anexos, ou anexo já na primeira página (sumário?): não sabe onde está o corpo
        return None
    numbers = [number for _, _, number, _ in found]
    if numbers != sorted(numbers):
        return None

    parts: list[frozenset[EditalPart]] = []
    current = EditalPart.BODY
    for page, text in enumerate(pages):
        present: set[EditalPart] = set()
        for index, (offset, _, part) in enumerate(headings[page]):
            # a parte anterior só conta se a página tem texto dela antes do título
            if index > 0 or len(_compact(text[:offset])) >= _MIN_TAIL_TEXT:
                present.add(current)
            current = part
        present.add(current)
        parts.append(frozenset(present))
    return parts


def pages_to_send(
    parts: Sequence[frozenset[EditalPart]], keep: Collection[EditalPart]
) -> list[int] | None:
    """Páginas (a partir de 0) com alguma parte de `keep`, ou None se forem todas (nada a
    cortar)."""
    pages = [page for page, present in enumerate(parts) if present & set(keep)]
    if not pages or len(pages) == len(parts):
        return None
    return pages


def _headings(text: str) -> list[tuple[int, int, EditalPart]]:
    """Títulos de anexo na página: (posição no texto, número do anexo, parte)."""
    lines = text.splitlines(keepends=True)
    found: list[tuple[int, int, EditalPart]] = []
    offset = 0
    for index, line in enumerate(lines):
        match = _HEADING.match(line.strip())
        # título na mesma linha ou nas duas seguintes ("ANEXO I" / "CONTEÚDO PROGRAMÁTICO...")
        title = (
            " ".join(
                [match["title"] or ""] + [following.strip() for following in lines[index + 1 :][:2]]
            )
            if match
            else ""
        )
        # linha de sumário (com pontilhado e página) não é o começo do anexo
        if match and not _TOC.search(line) and not _TOC.search(title):
            if found and len(_compact(text[found[-1][0] : offset])) < _MIN_ANNEX_TEXT:
                # dois anexos colados: lista dos anexos ou sumário sem pontilhado? Não dá para saber
                raise _Unsure
            found.append((offset, _number(match["number"]), _classify(title)))
        offset += len(line)
    return found


class _Unsure(Exception):
    pass


def _classify(title: str) -> EditalPart:
    compact = _compact(title)
    if any(word in compact for word in _KEEP):
        return EditalPart.OTHER_ANNEX
    if any(word in compact for word in _SYLLABUS):
        return EditalPart.SYLLABUS
    if any(word in compact for word in _DUTIES):
        return EditalPart.DUTIES
    return EditalPart.OTHER_ANNEX


def _number(text: str) -> int:
    if text.isdigit():
        return int(text)
    if text in ("ÚNICO", "UNICO"):
        return 1
    values = [_ROMAN[char] for char in text]
    return sum(-v if i + 1 < len(values) and v < values[i + 1] else v for i, v in enumerate(values))


def _compact(text: str) -> str:
    plain = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"\s+", "", plain).upper()
