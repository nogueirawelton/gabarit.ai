import pytest

from src.infrastructure.messaging.rabbitmq_consumer import is_last_delivery


@pytest.mark.parametrize(
    ("headers", "last"),
    [
        ({}, False),
        ({"x-delivery-count": 1}, False),
        ({"x-delivery-count": 2}, True),
        ({"x-delivery-count": 5}, True),
    ],
)
def test_ultima_entrega_conta_as_devolucoes(headers: dict[str, object], last: bool) -> None:
    assert is_last_delivery(headers, max_deliveries=3) is last
