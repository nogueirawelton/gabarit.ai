"""Tarefas e as versões de prompt que este worker sabe executar.

Cada versão é imutável: prompt melhorou ou o formato da saída mudou → versão nova (arquivo novo em
src/prompts/<tarefa>/ e, se preciso, modelo novo), e o core passa a pedir essa versão. A versão é
parte da chave de cache no core: resultado antigo fica para comparar.

O prompt de sistema (src/prompts/system.md) é o mesmo em todas as tarefas. Mudou o system.md?
Versão nova em todas as tarefas.

Tarefa sobre edital declara as partes que lê (`edital_parts`): o worker manda só essas páginas
(metade dos tokens na visão geral) ou, se não achar as partes com segurança, o PDF inteiro.
"""

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from pathlib import Path

from pydantic import BaseModel

from src.application.tasks import edital_overview, edital_syllabus
from src.domain.edital import EditalPart
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
    # o documento é um edital e a tarefa só lê estas partes (None = PDF inteiro)
    edital_parts: frozenset[EditalPart] | None = None
    # PDF recortado: as páginas que a saída cita viram as do edital original (recebe a saída e as
    # páginas mandadas, a partir de 0)
    original_pages: Callable[[dict[str, object], Sequence[int]], dict[str, object]] | None = None
    # parâmetros que a tarefa recebe (validados; vão no fim das instruções). None = nenhum
    params_model: type[BaseModel] | None = None

    def __post_init__(self) -> None:
        if self.edital_parts is not None and self.documents != 1:
            raise ValueError("recorte de edital só em tarefa de um documento")


def _prompt(task: str, version: str) -> str:
    return (PROMPTS / task / f"{version}.md").read_text(encoding="utf-8")


# tarefas que usam a LLM; ECHO é tratada à parte (sem LLM)
LLM_TASKS: dict[Task, dict[str, PromptVersion]] = {
    Task.EDITAL_OVERVIEW: {
        "v1": PromptVersion(
            instructions=_prompt("edital_overview", "v1"),
            output_model=edital_overview.EditalOverviewV1,
            # extração de quadros longos, com retificações: vale pensar mais que o padrão (medium)
            effort="high",
            documents=1,
            # cargos, vagas e datas ficam no corpo e em anexos como quadro de vagas e cronograma
            edital_parts=frozenset({EditalPart.BODY, EditalPart.OTHER_ANNEX}),
            original_pages=edital_overview.original_pages,
        ),
    },
    Task.EDITAL_SYLLABUS: {
        "v1": PromptVersion(
            instructions=_prompt("edital_syllabus", "v1"),
            output_model=edital_syllabus.EditalSyllabusV1,
            # copiar listas longas e achar o cargo (às vezes agrupado com outros): o padrão basta
            effort="medium",
            documents=1,
            edital_parts=frozenset({EditalPart.SYLLABUS}),
            original_pages=edital_syllabus.original_pages,
            params_model=edital_syllabus.EditalSyllabusParamsV1,
        ),
        # v2: tópicos numerados emendados no mesmo parágrafo viram itens separados (a v1 juntava
        # tudo num tópico só); bloco do cargo sem disciplinas vira "Conhecimentos Específicos"
        "v2": PromptVersion(
            instructions=_prompt("edital_syllabus", "v2"),
            output_model=edital_syllabus.EditalSyllabusV1,
            effort="medium",
            documents=1,
            edital_parts=frozenset({EditalPart.SYLLABUS}),
            original_pages=edital_syllabus.original_pages,
            params_model=edital_syllabus.EditalSyllabusParamsV1,
        ),
    },
}

ECHO_VERSIONS = frozenset({"v1"})
