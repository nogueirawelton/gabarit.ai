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

from src.application.tasks import (
    edital_overview,
    edital_syllabus,
    exam_paper,
    question_topics,
    topic_questions,
    topic_summary,
)
from src.domain.edital import EditalPart
from src.domain.extraction import Task
from src.domain.ports.llm_client import Effort

PROMPTS = Path(__file__).resolve().parent.parent.parent / "prompts"

SYSTEM_PROMPT = (PROMPTS / "system.md").read_text(encoding="utf-8")


# junta os trechos de um PDF longo: (resultado, páginas mandadas a partir de 0) de cada um
Merge = Callable[[Sequence[tuple[dict[str, object], Sequence[int]]]], dict[str, object]]


@dataclass(frozen=True)
class PromptVersion:
    instructions: str
    output_model: type[BaseModel]
    effort: Effort
    # quantos documentos a tarefa recebe (ex.: o edital; prova + gabarito)
    documents: int
    # recebe de `documents` até este número (ex.: a prova, e o gabarito quando é outro arquivo)
    max_documents: int | None = None
    # o documento é um edital e a tarefa só lê estas partes (None = PDF inteiro)
    edital_parts: frozenset[EditalPart] | None = None
    # PDF recortado: as páginas que a saída cita viram as do edital original (recebe a saída e as
    # páginas mandadas, a partir de 0)
    original_pages: Callable[[dict[str, object], Sequence[int]], dict[str, object]] | None = None
    # parâmetros que a tarefa recebe (validados; vão no fim das instruções). None = nenhum
    params_model: type[BaseModel] | None = None
    # PDF longo (o 1º documento): lido em trechos deste número de páginas, juntados por `merge`
    # (resultado e páginas mandadas de cada trecho, a partir de 0)
    chunk_pages: int | None = None
    merge: Merge | None = None

    def __post_init__(self) -> None:
        single = self.documents == 1 and self.max_documents in (None, 1)
        if self.edital_parts is not None and not single:
            raise ValueError("recorte de edital só em tarefa de um documento")

    def accepts(self, documents: int) -> bool:
        return self.documents <= documents <= (self.max_documents or self.documents)


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
        # v2: + quadro de provas (questões e peso por disciplina, formato, discursiva) e a prova de
        # cada cargo; lê as mesmas partes (o quadro fica no corpo)
        "v2": PromptVersion(
            instructions=_prompt("edital_overview", "v2"),
            output_model=edital_overview.EditalOverviewV2,
            effort="high",
            documents=1,
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
    Task.EXAM_PAPER: {
        "v1": PromptVersion(
            instructions=_prompt("exam_paper", "v1"),
            output_model=exam_paper.ExamPaperV1,
            # copiar texto e casar o gabarito: precisão, não raciocínio longo
            effort="medium",
            documents=1,
            max_documents=2,
            params_model=exam_paper.ExamPaperParamsV1,
            # ~3 questões por página: 16 páginas cabem com folga no limite de saída
            chunk_pages=16,
            merge=exam_paper.merge_chunks,
        ),
    },
    Task.QUESTION_TOPICS: {
        "v1": PromptVersion(
            instructions=_prompt("question_topics", "v1"),
            output_model=question_topics.QuestionTopicsV1,
            # casar assunto com tópico pede algum raciocínio, mas a lista já vem pronta
            effort="medium",
            # só texto (questões e tópicos nos parâmetros)
            documents=0,
            params_model=question_topics.QuestionTopicsParamsV1,
        ),
    },
    Task.TOPIC_QUESTIONS: {
        "v1": PromptVersion(
            instructions=_prompt("topic_questions", "v1"),
            output_model=topic_questions.TopicQuestionsV1,
            # criar questão boa (alternativas plausíveis, uma só certa, sem erro de conteúdo) pede
            # raciocínio: é a tarefa em que o effort mais pesa na qualidade
            effort="high",
            documents=0,
            params_model=topic_questions.TopicQuestionsParamsV1,
        ),
    },
    Task.TOPIC_SUMMARY: {
        "v1": PromptVersion(
            instructions=_prompt("topic_summary", "v1"),
            output_model=topic_summary.TopicSummaryV1,
            # material de estudo: errar conteúdo é o risco, vale pensar
            effort="high",
            documents=0,
            params_model=topic_summary.TopicSummaryParamsV1,
        ),
    },
}

ECHO_VERSIONS = frozenset({"v1"})
