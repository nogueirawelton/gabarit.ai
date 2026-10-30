"""EXAM_PAPER: prova (+ gabarito, se separado) → questões com alternativas e resposta."""

from collections.abc import Sequence
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ExamPaperParamsV1(BaseModel):
    """Tipo ou cor da prova (escolhe a coluna do gabarito), como a pessoa informou."""

    model_config = ConfigDict(extra="forbid")

    variant: str | None = Field(default=None, max_length=30)


class OptionV1(BaseModel):
    letter: str = Field(description='Letra da alternativa: "A", "B"...')
    text: str = Field(description="Texto da alternativa.")


class SharedTextV1(BaseModel):
    id: int = Field(description="Número deste texto-base na lista, a partir de 1.")
    content: str = Field(description="Texto-base compartilhado por várias questões, completo.")


class QuestionV1(BaseModel):
    number: int = Field(description="Número da questão na prova.")
    subject: str | None = Field(description="Disciplina sob a qual a questão aparece na prova.")
    kind: Literal["MULTIPLE_CHOICE", "TRUE_FALSE", "ESSAY"]
    text_id: int | None = Field(description="Texto-base (em texts) que a questão usa.")
    statement: str = Field(description="Enunciado completo, sem o texto-base compartilhado.")
    options: list[OptionV1] = Field(description="Alternativas na ordem; vazia em certo/errado.")
    answer: str | None = Field(description='Resposta do gabarito: letra, ou "C"/"E".')
    annulled: bool = Field(description="Anulada no gabarito.")
    has_image: bool = Field(description="Depende de figura, gráfico ou tabela.")
    page: int | None = Field(description="Página do PDF da prova onde a questão começa.")


class ExamPaperV1(BaseModel):
    variant: str | None = Field(description="Tipo ou cor da prova no cabeçalho.")
    answer_key_found: bool = Field(description="Achou o gabarito desta prova.")
    texts: list[SharedTextV1] = Field(description="Textos-base compartilhados.")
    questions: list[QuestionV1] = Field(description="Todas as questões, na ordem.")


def merge_chunks(parts: Sequence[tuple[dict[str, object], Sequence[int]]]) -> dict[str, object]:
    """Junta a leitura de uma prova lida em trechos (cada um com as páginas mandadas, a partir de
    0).

    Questão repetida na divisa dos trechos (a página de sobra) fica com a versão mais completa;
    textos-base ganham ids novos (cada trecho numera do 1); `page` volta a ser a da prova inteira.
    """
    by_number: dict[int, dict[str, object]] = {}
    texts: list[dict[str, object]] = []
    variant = None
    answer_key_found = False
    for result, pages in parts:
        variant = variant or result.get("variant")
        answer_key_found = answer_key_found or bool(result.get("answer_key_found"))
        text_ids: dict[int, int] = {}
        for text in result.get("texts") or []:
            same = next((t for t in texts if t["content"] == text["content"]), None)
            if same is None:
                same = {"id": len(texts) + 1, "content": text["content"]}
                texts.append(same)
            text_ids[text["id"]] = same["id"]
        for question in result.get("questions") or []:
            page = question.get("page")
            inside = isinstance(page, int) and 1 <= page <= len(pages)
            merged = question | {
                "text_id": text_ids.get(question.get("text_id")),
                "page": pages[page - 1] + 1 if inside else None,
            }
            current = by_number.get(question["number"])
            if current is None or _completeness(merged) > _completeness(current):
                by_number[question["number"]] = merged
    return {
        "variant": variant,
        "answer_key_found": answer_key_found,
        "texts": texts,
        "questions": [by_number[number] for number in sorted(by_number)],
    }


def _completeness(question: dict[str, object]) -> tuple[int, int, int]:
    """Mais completa: com resposta, mais alternativas, enunciado mais longo."""
    return (
        question.get("answer") is not None,
        len(question.get("options") or []),
        len(str(question.get("statement") or "")),
    )
