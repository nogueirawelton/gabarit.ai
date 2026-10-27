class ScraperError(Exception):
    """Base de todos os erros do domínio do scraper."""


class UnknownSourceError(ScraperError):
    """A fonte pedida não está registrada. Permanente: tentar de novo não resolve."""


class SourceUnavailableError(ScraperError):
    """A fonte não respondeu (rede, timeout, erro do servidor). Transitório: vale tentar de novo."""


class InvalidSourceDataError(ScraperError):
    """A fonte respondeu num formato inesperado. Provavelmente mudou: o código precisa de ajuste."""
