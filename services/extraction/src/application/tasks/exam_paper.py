"""EXAM_PAPER: prova (+ gabarito, se separado) → questões com alternativas e resposta."""

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
