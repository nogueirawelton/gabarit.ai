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


def original_pages(result: dict[str, object], sent: Sequence[int]) -> dict[str, object]:
    """`page` de cada cargo é a página do PDF recortado: vira a do edital original (a partir de 1).
    Página fora do recorte (o modelo inventou) vira None."""
    positions = []
    for position in result.get("positions") or []:
        page = position.get("page")
        inside = isinstance(page, int) and 1 <= page <= len(sent)
        positions.append(position | {"page": sent[page - 1] + 1 if inside else None})
    return result | {"positions": positions}
