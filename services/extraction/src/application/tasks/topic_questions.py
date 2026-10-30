"""TOPIC_QUESTIONS: tópico de um conteúdo + banca → questões inéditas no estilo dela (sem PDF).

Os parâmetros não têm nada do usuário: a mesma banca e o mesmo tópico geram uma vez para todos.
"""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


class TopicQuestionsParamsV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    board: str | None = Field(default=None, max_length=100)
    answer_format: Literal["MULTIPLE_CHOICE", "TRUE_FALSE"]
    options_count: int = Field(ge=2, le=5)
    subject: str = Field(min_length=1, max_length=300)
    topic: str = Field(min_length=1, max_length=4000)
    count: int = Field(ge=1, le=10)


class TopicQuestionsParamsV2(TopicQuestionsParamsV1):
    # rodada de geração do tópico (1, 2...) e enunciados das anteriores, para não repetir
    round: int = Field(ge=1, le=1000)
    avoid: list[Annotated[str, Field(max_length=300)]] = Field(max_length=40)


class GeneratedOptionV1(BaseModel):
    letter: str = Field(description='Letra: "A", "B"...')
    text: str


class GeneratedQuestionV1(BaseModel):
    statement: str = Field(description="Enunciado completo (em certo/errado, o item para julgar).")
    options: list[GeneratedOptionV1] = Field(description="Alternativas; vazia em certo/errado.")
    answer: str = Field(description='Letra correta, ou "C"/"E" em certo/errado.')
    explanation: str = Field(description="Comentário: por que a resposta está certa.")


class TopicQuestionsV1(BaseModel):
    questions: list[GeneratedQuestionV1]


class GeneratedQuestionV2(GeneratedQuestionV1):
    base_text: str | None = Field(
        description="Texto-base completo que a questão cita (interpretação, caso, tabela); "
        "null se o enunciado se basta."
    )


class TopicQuestionsV2(BaseModel):
    questions: list[GeneratedQuestionV2]
