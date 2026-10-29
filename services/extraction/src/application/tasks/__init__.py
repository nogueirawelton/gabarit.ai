"""Tarefas e as versões de prompt que este worker sabe executar.

Cada versão é imutável: prompt melhorou ou o formato da saída mudou → versão nova (arquivo novo em
src/prompts/<tarefa>/ e, se preciso, modelo novo), e o core passa a pedir essa versão. A versão é
parte da chave de cache no core: resultado antigo fica para comparar.

O prompt de sistema (src/prompts/system.md) é o mesmo em todas as tarefas, para o cache de prompt
do documento valer de uma tarefa para outra. Mudou o system.md? Versão nova em todas as tarefas.
"""

from dataclasses import dataclass
from pathlib import Path

from pydantic import BaseModel

from src.application.tasks.edital_overview import EditalOverviewV1
from src.domain.extraction import Task
from src.domain.ports.llm_client import Effort

PROMPTS = Path(__file__).resolve().parent.parent.parent / "prompts"

SYSTEM_PROMPT = (PROMPTS / "system.md").read_text(encoding="utf-8")


@dataclass(frozen=True)
class PromptVersion:
    instructions: str
    output_model: type[BaseModel]
    effort: Effort
    # quantos documentos a tarefa recebe (ex.: o edital; prova + gabarito)
    documents: int


def _prompt(task: str, version: str) -> str:
    return (PROMPTS / task / f"{version}.md").read_text(encoding="utf-8")


# tarefas que usam a LLM; ECHO é tratada à parte (sem LLM)
LLM_TASKS: dict[Task, dict[str, PromptVersion]] = {
    Task.EDITAL_OVERVIEW: {
        "v1": PromptVersion(
            instructions=_prompt("edital_overview", "v1"),
            output_model=EditalOverviewV1,
            # extração de quadros longos, com retificações: vale pensar mais que o padrão (medium)
            effort="high",
            documents=1,
        ),
    },
}

ECHO_VERSIONS = frozenset({"v1"})
