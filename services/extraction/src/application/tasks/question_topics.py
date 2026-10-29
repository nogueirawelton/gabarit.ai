"""QUESTION_TOPICS: questões de uma prova + conteúdo de um cargo → o tópico de cada questão.

Sem documento: o core manda o texto já lido (questões da EXAM_PAPER, tópicos da EDITAL_SYLLABUS).
"""

from pydantic import BaseModel, ConfigDict, Field


class TopicV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str = Field(min_length=1, max_length=20)
    subject: str = Field(max_length=300)
    text: str = Field(max_length=4000)


class QuestionV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ordinal: int
    subject: str | None = Field(default=None, max_length=300)
    statement: str = Field(max_length=4000)
    options: list[str] = Field(default_factory=list, max_length=10)


class QuestionTopicsParamsV1(BaseModel):
    """`exam_reading_id`/`syllabus_reading_id`: de quais leituras vêm as questões e os tópicos (o
    core usa na volta; aqui só entram na chave de cache)."""

    model_config = ConfigDict(extra="forbid")

    exam_reading_id: str
    syllabus_reading_id: str
    topics: list[TopicV1] = Field(min_length=1, max_length=2000)
    questions: list[QuestionV1] = Field(min_length=1, max_length=300)


class ClassificationV1(BaseModel):
    ordinal: int = Field(description="Número de ordem da questão (ordinal).")
    topic: str | None = Field(description="Código do tópico, ou null se nenhum cobre a questão.")


class QuestionTopicsV1(BaseModel):
    classifications: list[ClassificationV1] = Field(description="Uma por questão.")
