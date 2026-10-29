"""EDITAL_SYLLABUS: conteúdo programático do edital + cargo escolhido → disciplinas e tópicos."""

from collections.abc import Sequence

from pydantic import BaseModel, ConfigDict, Field


class SyllabusPositionV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=300)
    specialty: str | None = Field(default=None, max_length=300)


class EditalSyllabusParamsV1(BaseModel):
    """O cargo como a visão geral (EDITAL_OVERVIEW) leu no quadro de vagas."""

    model_config = ConfigDict(extra="forbid")

    position: SyllabusPositionV1


class SubjectV1(BaseModel):
    name: str = Field(description='Disciplina como o edital escreve, ex.: "Língua Portuguesa".')
    group: str | None = Field(
        description='Bloco da disciplina, quando o edital agrupa: ex.: "Conhecimentos Específicos".'
    )
    topics: list[str] = Field(
        description="Tópicos de primeiro nível na ordem do edital, sem a numeração; subtópicos no "
        "texto do tópico."
    )
    page: int | None = Field(description="Página do PDF onde a disciplina começa.")


class EditalSyllabusV1(BaseModel):
    position_found: bool = Field(description="Achou o conteúdo programático do cargo pedido.")
    subjects: list[SubjectV1] = Field(description="Disciplinas do cargo, comuns e específicas.")


def original_pages(result: dict[str, object], sent: Sequence[int]) -> dict[str, object]:
    """`page` de cada disciplina é a página do PDF recortado: vira a do edital original."""
    subjects = []
    for subject in result.get("subjects") or []:
        page = subject.get("page")
        inside = isinstance(page, int) and 1 <= page <= len(sent)
        subjects.append(subject | {"page": sent[page - 1] + 1 if inside else None})
    return result | {"subjects": subjects}
