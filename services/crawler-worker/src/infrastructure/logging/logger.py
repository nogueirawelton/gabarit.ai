import logging
import sys
import json
from datetime import datetime


class Logger:
    def __init__(self, service_name: str = "gabarit-crawler"):
        self.logger = logging.getLogger(service_name)
        self.logger.setLevel(logging.INFO)

        handler = logging.StreamHandler(sys.stdout)

        formatter = logging.Formatter(
            '{"timestamp": "%(asctime)s", "level": "%(levelname)s", "service": "%(name)s", "message": "%(message)s"}'
        )
        handler.setFormatter(formatter)

        if not self.logger.handlers:
            self.logger.addHandler(handler)

    def info(self, message: str, **kwargs):
        self.logger.info(self._format(message, kwargs))

    def error(self, message: str, error: Exception = None, **kwargs):
        if error:
            kwargs["exception"] = str(error)
        self.logger.error(self._format(message, kwargs))

    def _format(self, message: str, extra: dict) -> str:
        # Mescla a mensagem com dados extras (ID do job, URL, etc)
        data = {"msg": message, **extra}
        return json.dumps(data)


logger = Logger()