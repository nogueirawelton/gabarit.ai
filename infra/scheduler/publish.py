#!/usr/bin/env python3
"""Publica uma mensagem num exchange do RabbitMQ pela API HTTP do management.

Uso: publish <exchange> <routing_key> '<json>'

Cada execução leva um message_id novo (os consumidores usam como correlation_id).
Sai com erro se o broker não rotear a mensagem: comando sem fila ligada não pode sumir calado.
Só stdlib, de propósito: a imagem do agendador não tem dependências para manter.
"""

import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime
from uuid import uuid4


def log(level: str, msg: str, **fields: object) -> None:
    entry = {"ts": datetime.now(UTC).isoformat(), "level": level, "logger": "publish", "msg": msg}
    print(json.dumps(entry | fields, ensure_ascii=False), flush=True)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    exchange, routing_key, payload = argv
    try:
        json.loads(payload)
    except json.JSONDecodeError as e:
        log("ERROR", "payload não é JSON", error=str(e))
        return 2

    api = os.environ.get("RABBITMQ_API_URL", "http://rabbitmq:15672")
    vhost = urllib.parse.quote(os.environ.get("RABBITMQ_VHOST", "/"), safe="")
    user, password = os.environ["RABBITMQ_USER"], os.environ["RABBITMQ_PASSWORD"]
    message_id = str(uuid4())

    body = {
        "routing_key": routing_key,
        "payload": payload,
        "payload_encoding": "string",
        "properties": {
            "message_id": message_id,
            "content_type": "application/json",
            "delivery_mode": 2,
            "timestamp": int(datetime.now(UTC).timestamp()),
        },
    }
    request = urllib.request.Request(
        f"{api}/api/exchanges/{vhost}/{urllib.parse.quote(exchange, safe='')}/publish",
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode(),
        },
        method="POST",
    )
    fields = {"exchange": exchange, "routing_key": routing_key, "message_id": message_id}
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            routed = json.load(response).get("routed", False)
    except (urllib.error.URLError, TimeoutError) as e:
        log("ERROR", "falha ao publicar", error=str(e), **fields)
        return 1

    if not routed:
        log("ERROR", "mensagem não roteada: nenhuma fila ligada a essa routing key", **fields)
        return 1
    log("INFO", "publicado", **fields)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
