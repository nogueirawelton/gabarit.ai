"""TOPIC_SUMMARY: disciplina + tópico → resumo, o que mais cai, pegadinhas, leis e leituras.

Sem documento e sem nada do usuário nos parâmetros: o mesmo tópico gera uma vez para todos.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class TopicSummaryParamsV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    subject: str = Field(min_length=1, max_length=300)
    topic: str = Field(min_length=1, max_length=4000)


class LawV1(BaseModel):
    name: str = Field(description='Como é citada, ex.: "Lei nº 9.784/1999".')
    kind: Literal["CONSTITUTION", "LAW", "COMPLEMENTARY_LAW", "DECREE", "OTHER"]
    number: str | None = Field(description='Número sem pontos, ex.: "9784"; null na Constituição.')
    year: int | None = Field(description="Ano da norma.")
    articles: list[str] = Field(description='Artigos centrais, ex.: "art. 37".')


class ReadingV1(BaseModel):
    author: str
    title: str


class TopicSummaryV1(BaseModel):
    summary: str = Field(description="Resumo do tópico, em parágrafos curtos.")
    key_points: list[str] = Field(description="O que mais cai em prova.")
    pitfalls: list[str] = Field(description="Pegadinhas comuns de banca.")
    laws: list[LawV1] = Field(description="Normas que o tópico cobra (vazia se não é normativo).")
    readings: list[ReadingV1] = Field(description="Obras de referência consagradas (até 3).")
