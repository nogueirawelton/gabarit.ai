class ExtractionError(Exception):
    """Base de todos os erros do domínio do extraction."""


class InvalidInputError(ExtractionError):
    """O pedido não tem como dar certo (PDF ausente ou corrompido, prompt desconhecido...).

    Permanente: tentar de novo não resolve. Vira extraction.failed com retryable=false.
    """


class TransientError(ExtractionError):
    """Falha passageira (rede, MinIO fora, limite de uso da API). Vale tentar de novo."""


class DocumentNotFoundError(InvalidInputError):
    """O documento pedido não existe no armazenamento."""
