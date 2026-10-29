"""EDITAL_OVERVIEW: edital inteiro → órgão, banca, ano, datas e cargos."""

from collections.abc import Sequence
from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

EducationLevel = Literal["FUNDAMENTAL", "MEDIO", "TECNICO", "SUPERIOR"]


class PositionV1(BaseModel):
    name: str = Field(description="Nome do cargo como o edital escreve, sem código nem área.")
    specialty: str | None = Field(
        description="Especialidade ou área do cargo, quando o cargo se divide em áreas."
    )
    education_level: EducationLevel | None = Field(
        description="Escolaridade exigida (técnico = ensino médio técnico/profissionalizante)."
    )
    openings: str | None = Field(
        description='Vagas como o edital escreve, ex.: "12", "3 + CR", "Cadastro reserva".'
    )
    salary: str | None = Field(
        description='Remuneração inicial como o edital escreve, ex.: "R$ 8.529,67".'
    )
    page: int | None = Field(description="Página do PDF do quadro de vagas onde o cargo aparece.")


class EditalOverviewV1(BaseModel):
    organ: str | None = Field(description="Órgão ou entidade que realiza o concurso (não a banca).")
    board: str | None = Field(description="Banca organizadora, ex.: FGV, Cebraspe, FCC, Vunesp.")
    year: int | None = Field(description="Ano do edital.")
    registration_start: date | None = Field(description="Início das inscrições.")
    registration_end: date | None = Field(description="Fim das inscrições.")
    exam_date: date | None = Field(description="Data da prova objetiva, se o edital fixa o dia.")
    positions: list[PositionV1] = Field(description="Todos os cargos oferecidos.")


class PositionV2(PositionV1):
    exam_id: int | None = Field(description="id da prova (em exams) que vale para este cargo.")


class ExamSectionV2(BaseModel):
    block: str | None = Field(
        description='Bloco do quadro de provas, quando agrupa, ex.: "Conhecimentos Básicos".'
    )
    subject: str = Field(description="Disciplina como o quadro de provas escreve.")
    questions: int | None = Field(description="Número de questões da disciplina.")
    weight: float | None = Field(description="Peso de cada questão (ou da disciplina).")


class ExamV2(BaseModel):
    id: int = Field(description="Número desta prova na lista, a partir de 1.")
    applies_to: str = Field(
        description='A quem a prova vale, como o edital escreve: ex.: "Cargos de nível superior".'
    )
    answer_format: Literal["MULTIPLE_CHOICE", "TRUE_FALSE"] | None = Field(
        description="Alternativas (A–E, A–D) ou certo/errado."
    )
    total_questions: int | None = Field(description="Total de questões da prova objetiva.")
    sections: list[ExamSectionV2] = Field(description="Disciplinas do quadro de provas.")
    essay: bool = Field(description="Tem discursiva, redação ou estudo de caso.")


class EditalOverviewV2(EditalOverviewV1):
    """v1 + o quadro de provas: as provas distintas e, em cada cargo, qual vale para ele."""

    positions: list[PositionV2] = Field(description="Todos os cargos oferecidos.")
    exams: list[ExamV2] = Field(description="Provas objetivas distintas do quadro de provas.")


def original_pages(result: dict[str, object], sent: Sequence[int]) -> dict[str, object]:
    """`page` de cada cargo é a página do PDF recortado: vira a do edital original (a partir de 1).
    Página fora do recorte (o modelo inventou) vira None."""
    positions = []
    for position in result.get("positions") or []:
        page = position.get("page")
        inside = isinstance(page, int) and 1 <= page <= len(sent)
        positions.append(position | {"page": sent[page - 1] + 1 if inside else None})
    return result | {"positions": positions}
