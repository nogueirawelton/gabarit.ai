from datetime import date

from pydantic import BaseModel, ConfigDict, Field, model_validator

from src.domain.entities.position import Position


class Exam(BaseModel):
    """Concurso encontrado numa fonte. Identidade: (source, external_id)."""

    model_config = ConfigDict(frozen=True, str_strip_whitespace=True)

    source: str = Field(min_length=1)
    external_id: str = Field(min_length=1)
    title: str = Field(min_length=1)
    url: str = Field(min_length=1)
    # None em concursos nacionais
    uf: str | None = None
    region: str | None = None
    education_level: str | None = None
    # Texto cru da fonte, ex.: "454 vagas até R$ 8.628,87"
    openings: str | None = None
    registration_start: date | None = None
    registration_end: date | None = None
    # Situação das inscrições como a fonte escreve, ex.: "Suspenso", "Prorrogado"
    registration_note: str | None = None
    positions: tuple[Position, ...] = ()

    @model_validator(mode="after")
    def _check(self) -> "Exam":
        names = [p.name for p in self.positions]
        if len(names) != len(set(names)):
            raise ValueError("cargo repetido no mesmo concurso")
        if (
            self.registration_start
            and self.registration_end
            and self.registration_end < self.registration_start
        ):
            raise ValueError("fim das inscrições antes do início")
        return self
