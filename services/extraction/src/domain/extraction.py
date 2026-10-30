from dataclasses import dataclass
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field


class Task(StrEnum):
    # lê os documentos e devolve os parâmetros, sem LLM: testa o caminho core → worker → core
    ECHO = "ECHO"
    # edital inteiro → órgão, banca, ano, data da prova, cargos
    EDITAL_OVERVIEW = "EDITAL_OVERVIEW"
    # conteúdo programático do edital + cargo escolhido → disciplinas e tópicos daquele cargo
    EDITAL_SYLLABUS = "EDITAL_SYLLABUS"
    # prova (+ gabarito, se separado) → questões com alternativas e resposta
    EXAM_PAPER = "EXAM_PAPER"
    # questões já lidas + conteúdo de um cargo → o tópico de cada questão (sem documento)
    QUESTION_TOPICS = "QUESTION_TOPICS"
    # tópico + banca → questões inéditas no estilo dela (sem documento)
    TOPIC_QUESTIONS = "TOPIC_QUESTIONS"
    # disciplina + tópico → resumo, o que mais cai, pegadinhas, leis e leituras (sem documento)
    TOPIC_SUMMARY = "TOPIC_SUMMARY"


class DocumentRef(BaseModel):
    """Um PDF de entrada: o hash do conteúdo e onde ele está no bucket."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    key: str = Field(min_length=1)


@dataclass(frozen=True)
class Document:
    """Um PDF já lido e conferido (o conteúdo bate com o sha256)."""

    sha256: str
    content: bytes


class Usage(BaseModel):
    """Tokens gastos, para o core calcular o custo. Zero quando a tarefa não usa LLM."""

    model_config = ConfigDict(frozen=True)

    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_input_tokens: int = 0
    cache_creation_input_tokens: int = 0


class ExtractionOutput(BaseModel):
    """O que uma tarefa produziu: o JSON já validado pelo schema dela."""

    model_config = ConfigDict(frozen=True)

    result: dict[str, object]
    usage: Usage = Usage()
    # None quando a tarefa não usa LLM
    model: str | None = None
