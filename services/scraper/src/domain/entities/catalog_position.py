from pydantic import BaseModel, ConfigDict, Field


class CatalogPosition(BaseModel):
    """Cargo do catálogo da fonte (ex.: "Enfermeiro"), que agrupa os nomes crus dos concursos.

    Identidade: (source, slug).
    """

    model_config = ConfigDict(frozen=True, str_strip_whitespace=True)

    source: str = Field(min_length=1)
    slug: str = Field(min_length=1)
    name: str = Field(min_length=1)
