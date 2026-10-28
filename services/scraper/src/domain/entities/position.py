from pydantic import BaseModel, ConfigDict, Field


class Position(BaseModel):
    """Cargo de um concurso. Só existe dentro do Exam; a identidade é o nome dentro do concurso."""

    model_config = ConfigDict(frozen=True, str_strip_whitespace=True)

    # Nome como a fonte escreve, sem normalizar: padronizar entre bancas é papel do `core`
    name: str = Field(min_length=1)
    # Cargos do catálogo da fonte (CatalogPosition.slug) em que este nome apareceu
    catalog_slugs: tuple[str, ...] = ()
