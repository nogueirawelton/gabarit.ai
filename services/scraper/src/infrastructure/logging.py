"""Logs estruturados em JSON no stdout, com o message_id da mensagem em processamento."""

import json
import logging
import sys
from contextvars import ContextVar
from datetime import UTC, datetime

message_id_var: ContextVar[str] = ContextVar("message_id", default="-")

# atributos padrão do LogRecord; o resto veio de `extra=` e entra no JSON
_RESERVED = set(vars(logging.makeLogRecord({}))) | {"message", "asctime", "taskName"}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        entry: dict[str, object] = {
            "ts": datetime.fromtimestamp(record.created, UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
            "message_id": message_id_var.get(),
        }
        entry.update({k: v for k, v in vars(record).items() if k not in _RESERVED})
        if record.exc_info:
            entry["exc"] = self.formatException(record.exc_info)
        return json.dumps(entry, ensure_ascii=False, default=str)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    logging.basicConfig(level=level.upper(), handlers=[handler], force=True)
    # libs muito verbosas em INFO
    for noisy in ("httpx", "httpx2", "mcp", "aiormq", "aio_pika"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
