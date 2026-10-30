import { ArrowLeft, ArrowRight, Bookmark, Check, Clock, ExternalLink, FileText, Flag, ImageIcon, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";

import { ApiError, isUuid, type ReportReason, type Simulation, type SimulationQuestion } from "../api";
import { ExamDetailSkeleton } from "../components/Skeleton";
import { useAnswer, useFinishSimulation, useReportQuestion, useSimulation } from "../data/simulations";
import { primaryButton, secondaryButton } from "./StudiesPage";

/** Simulado: a prova em andamento (uma questão por vez, cronômetro, mapa) ou, entregue, o resultado. */
export function SimulationPage() {
  const id = useParams().id ?? "";
  const simulation = useSimulation(id);

  if (!isUuid(id) || (simulation.error instanceof ApiError && simulation.error.status === 404)) {
    return <h1 className="text-2xl font-bold tracking-tight">Simulado não encontrado</h1>;
  }
  if (simulation.isPending) {
    return <ExamDetailSkeleton />;
  }
  if (simulation.isError) {
    return <p className="text-sm text-red-700 dark:text-red-300">Não foi possível carregar o simulado. Recarregue a página.</p>;
  }
  return simulation.data.finished ? <Result simulation={simulation.data} /> : <Running simulation={simulation.data} />;
}

function BackLink({ simulation }: { simulation: Simulation }) {
  return (
    <Link
      to={`/meus-concursos/${simulation.studyTargetId}?passo=estudar`}
      className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Voltar ao concurso
    </Link>
  );
}

/** Agora, de segundo em segundo (para o cronômetro). */
function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

function formatClock(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

/** Questões marcadas para revisar: só neste navegador (é ajuda da tela, não resposta). */
function useMarked(simulationId: string) {
  const key = `simulado:${simulationId}:revisar`;
  const [marked, setMarked] = useState<Set<string>>(() => {
    try {
      return new Set(JSON.parse(localStorage.getItem(key) ?? "[]") as string[]);
    } catch {
      return new Set();
    }
  });
  function toggle(questionId: string) {
    setMarked((current) => {
      const next = new Set(current);
      if (next.has(questionId)) {
        next.delete(questionId);
      } else {
        next.add(questionId);
      }
      try {
        localStorage.setItem(key, JSON.stringify([...next]));
      } catch {
        // sem armazenamento (aba anônima): a marcação vale até fechar a página
      }
      return next;
    });
  }
  return { marked, toggle };
}

function Running({ simulation }: { simulation: Simulation }) {
  const [index, setIndex] = useState(0);
  const answer = useAnswer(simulation.id);
  const finish = useFinishSimulation(simulation.id);
  const { marked, toggle } = useMarked(simulation.id);
  const now = useNow();
  const finishing = useRef(false);

  const questions = simulation.questions;
  const question = questions[Math.min(index, questions.length - 1)];
  const answered = questions.filter((q) => q.chosen).length;
  const remaining = simulation.deadline ? new Date(simulation.deadline).getTime() - now : null;
  const elapsed = now - new Date(simulation.startedAt).getTime();

  // tempo acabou: entrega sozinho (o servidor também não aceita mais resposta)
  useEffect(() => {
    if (remaining !== null && remaining <= 0 && !finishing.current) {
      finishing.current = true;
      finish.mutate();
    }
  }, [remaining, finish]);

  function choose(letter: string) {
    // tocar de novo na marcada desmarca
    answer.mutate({ questionId: question.id, answer: question.chosen === letter ? null : letter });
  }

  function submit() {
    const blank = questions.length - answered;
    const message =
      blank > 0
        ? `Entregar o simulado? ${blank} ${blank === 1 ? "questão está" : "questões estão"} em branco.`
        : "Entregar o simulado?";
    if (window.confirm(message)) {
      finish.mutate();
    }
  }

  return (
    <>
      <BackLink simulation={simulation} />
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">Simulado · {simulation.title}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {answered} de {questions.length} respondidas
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-semibold tabular-nums ${
              remaining !== null && remaining < 5 * 60 * 1000
                ? "bg-amber-50 text-amber-800 dark:bg-amber-400/10 dark:text-amber-300"
                : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300"
            }`}
            role="timer"
            aria-label={remaining !== null ? "Tempo restante" : "Tempo de prova"}
          >
            <Clock className="size-4" aria-hidden="true" />
            {remaining !== null ? formatClock(remaining) : formatClock(elapsed)}
          </span>
          <button type="button" onClick={submit} disabled={finish.isPending} className={primaryButton}>
            {finish.isPending ? "Entregando…" : "Entregar"}
          </button>
        </div>
      </div>
      {finish.isError && (
        <p role="alert" className="mt-3 text-sm text-red-700 dark:text-red-300">
          Não foi possível entregar agora. Tente de novo.
        </p>
      )}

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_16rem]">
        <section className="rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
          <QuestionBody question={question} position={index + 1} />

          <ol className="mt-5 grid gap-2">
            {question.kind === "TRUE_FALSE"
              ? [
                  { letter: "C", text: "Certo" },
                  { letter: "E", text: "Errado" },
                ].map((option) => (
                  <Option key={option.letter} option={option} chosen={question.chosen === option.letter} onChoose={choose} />
                ))
              : question.options.map((option) => (
                  <Option key={option.letter} option={option} chosen={question.chosen === option.letter} onChoose={choose} />
                ))}
          </ol>
          {answer.isError && (
            <p role="alert" className="mt-3 text-sm text-red-700 dark:text-red-300">
              {answer.error instanceof ApiError && answer.error.detail
                ? answer.error.detail
                : "Não foi possível salvar a resposta. Tente de novo."}
            </p>
          )}

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5 dark:border-slate-800">
            <button
              type="button"
              onClick={() => setIndex(index - 1)}
              disabled={index === 0}
              className={`${secondaryButton} disabled:opacity-40`}
            >
              <ArrowLeft className="size-4" aria-hidden="true" />
              Anterior
            </button>
            <button
              type="button"
              onClick={() => toggle(question.id)}
              aria-pressed={marked.has(question.id)}
              className={`inline-flex items-center gap-1.5 text-sm font-semibold ${
                marked.has(question.id) ? "text-amber-700 dark:text-amber-300" : "text-slate-600 dark:text-slate-400"
              }`}
            >
              <Bookmark className={`size-4 ${marked.has(question.id) ? "fill-current" : ""}`} aria-hidden="true" />
              {marked.has(question.id) ? "Marcada para revisar" : "Revisar depois"}
            </button>
            <button
              type="button"
              onClick={() => setIndex(index + 1)}
              disabled={index >= questions.length - 1}
              className={`${secondaryButton} disabled:opacity-40`}
            >
              Próxima
              <ArrowRight className="size-4" aria-hidden="true" />
            </button>
          </div>
        </section>

        <nav aria-label="Questões" className="rounded-2xl border border-slate-200 bg-white p-4 lg:sticky lg:top-24 dark:border-slate-800 dark:bg-slate-900">
          <p className="text-sm font-semibold">Questões</p>
          <ol className="mt-3 grid grid-cols-8 gap-1.5 sm:grid-cols-10 lg:grid-cols-6">
            {questions.map((q, position) => (
              <li key={q.id}>
                <button
                  type="button"
                  onClick={() => setIndex(position)}
                  aria-label={`Questão ${position + 1}${q.chosen ? ", respondida" : ""}${marked.has(q.id) ? ", marcada para revisar" : ""}`}
                  aria-current={position === index ? "step" : undefined}
                  className={`relative grid h-8 w-full place-items-center rounded-md text-xs font-semibold tabular-nums transition ${
                    q.chosen
                      ? "bg-indigo-600 text-white dark:bg-indigo-500"
                      : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400"
                  } ${position === index ? "ring-2 ring-indigo-600 ring-offset-2 dark:ring-indigo-400 dark:ring-offset-slate-900" : ""}`}
                >
                  {position + 1}
                  {marked.has(q.id) && (
                    <span className="absolute -top-1 -right-1 size-2.5 rounded-full bg-amber-500" aria-hidden="true" />
                  )}
                </button>
              </li>
            ))}
          </ol>
          <ul className="mt-4 space-y-1 text-xs text-slate-500 dark:text-slate-400">
            <li className="flex items-center gap-2">
              <span className="size-3 rounded bg-indigo-600 dark:bg-indigo-500" aria-hidden="true" /> Respondida
            </li>
            <li className="flex items-center gap-2">
              <span className="size-2.5 rounded-full bg-amber-500" aria-hidden="true" /> Revisar depois
            </li>
          </ul>
        </nav>
      </div>
    </>
  );
}

/** Texto-base (recolhível), número, disciplina e enunciado. */
function QuestionBody({ question, position }: { question: SimulationQuestion; position: number }) {
  return (
    <>
      <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
        <span>
          Questão {position}
          {question.subject && <span className="text-slate-500 dark:text-slate-400"> · {question.subject}</span>}
        </span>
        {question.generated && (
          <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[11px] tracking-normal text-violet-700 normal-case dark:bg-violet-500/10 dark:text-violet-300">
            gerada por IA
          </span>
        )}
      </p>
      {question.baseText && (
        <details open className="mt-3 rounded-lg bg-slate-50 dark:bg-slate-800/50">
          <summary className="cursor-pointer px-4 py-2 text-sm font-medium text-slate-600 dark:text-slate-400">
            Texto-base
          </summary>
          <div className="scroll-thin max-h-80 overflow-y-auto px-4 pb-4 text-sm leading-relaxed whitespace-pre-line">
            {question.baseText}
          </div>
        </details>
      )}
      {question.hasImage && (
        <div className="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-400/10 dark:text-amber-300">
          <p className="flex items-start gap-2">
            <ImageIcon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {question.pdfUrl
              ? "Esta questão tem figura, gráfico ou tabela. Veja na prova; a descrição está entre colchetes no enunciado."
              : `Esta questão tem figura, gráfico ou tabela${question.page ? ` (página ${question.page} da prova)` : ""}. A descrição está entre colchetes no enunciado.`}
          </p>
          {question.pdfUrl && (
            <a
              href={question.pdfUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex shrink-0 items-center gap-1.5 rounded-md bg-amber-100 px-2.5 py-1 font-semibold text-amber-900 transition hover:bg-amber-200 dark:bg-amber-400/20 dark:text-amber-200 dark:hover:bg-amber-400/30"
            >
              Abrir a prova na página {question.page}
              <ExternalLink className="size-3.5" aria-hidden="true" />
            </a>
          )}
        </div>
      )}
      <p className="mt-3 leading-relaxed whitespace-pre-line">{question.statement}</p>
      {/* sem figura: o acesso à prova fica discreto */}
      {question.pdfUrl && !question.hasImage && (
        <a
          href={question.pdfUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-2 inline-flex items-center gap-1 text-xs text-slate-500 hover:text-indigo-700 dark:text-slate-400 dark:hover:text-indigo-300"
        >
          <FileText className="size-3.5" aria-hidden="true" />
          Ver na prova (página {question.page})
        </a>
      )}
    </>
  );
}

function Option({
  option,
  chosen,
  onChoose,
}: {
  option: { letter: string; text: string };
  chosen: boolean;
  onChoose: (letter: string) => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={() => onChoose(option.letter)}
        aria-pressed={chosen}
        className={`flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition ${
          chosen
            ? "border-indigo-600 bg-indigo-50 dark:border-indigo-400 dark:bg-indigo-500/10"
            : "border-slate-200 hover:border-slate-400 dark:border-slate-800 dark:hover:border-slate-600"
        }`}
      >
        <span
          className={`grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold ${
            chosen ? "bg-indigo-600 text-white dark:bg-indigo-500" : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
          }`}
        >
          {option.letter}
        </span>
        <span className="pt-0.5">{option.text}</span>
      </button>
    </li>
  );
}

type Filter = "todas" | "erradas" | "branco";

/** Resultado: nota, por disciplina e a correção questão a questão (todas, erradas ou em branco). */
function Result({ simulation }: { simulation: Simulation }) {
  const result = simulation.result;
  const [filter, setFilter] = useState<Filter>("todas");
  if (!result) {
    return null;
  }
  const scored = result.total - result.unscored;
  const percent = scored > 0 ? Math.round((result.correct / scored) * 100) : 0;
  const shown = simulation.questions.filter((q) =>
    filter === "erradas" ? q.correct === false : filter === "branco" ? !q.chosen && !q.annulled : true,
  );

  return (
    <>
      <BackLink simulation={simulation} />
      <h1 className="mt-4 text-xl font-bold tracking-tight sm:text-2xl">Resultado · {simulation.title}</h1>

      <section className="mt-6 grid gap-4 sm:grid-cols-[auto_1fr]">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center dark:border-slate-800 dark:bg-slate-900">
          <p className="text-5xl font-bold tracking-tight tabular-nums">{percent}%</p>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {result.correct} de {scored} certas
          </p>
          {result.net !== null && (
            <p className="mt-2 text-sm font-semibold">
              Nota líquida: {result.net}
              <span className="block text-xs font-normal text-slate-500 dark:text-slate-400">uma errada anula uma certa</span>
            </p>
          )}
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
          <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            {[
              ["Certas", result.correct, "text-emerald-700 dark:text-emerald-400"],
              ["Erradas", result.wrong, "text-red-700 dark:text-red-400"],
              ["Em branco", result.blank, "text-slate-600 dark:text-slate-400"],
              ["Anuladas", result.annulled, "text-slate-600 dark:text-slate-400"],
            ].map(([label, value, tone]) => (
              <div key={label as string}>
                <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
                <dd className={`text-2xl font-bold tabular-nums ${tone}`}>{value}</dd>
              </div>
            ))}
          </dl>
          {result.unscored > 0 && (
            <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
              {result.unscored} {result.unscored === 1 ? "questão ficou" : "questões ficaram"} fora da nota (sem resposta no gabarito).
            </p>
          )}
        </div>
      </section>

      <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="font-semibold">Por disciplina</h2>
        <ul className="mt-3 space-y-3">
          {result.bySubject.map((subject) => {
            const share = subject.total > 0 ? Math.round((subject.correct / subject.total) * 100) : 0;
            return (
              <li key={subject.subject}>
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="font-medium">{subject.subject}</span>
                  <span className="shrink-0 tabular-nums text-slate-600 dark:text-slate-400">
                    {subject.correct}/{subject.total} · {share}%
                  </span>
                </div>
                <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                  <div className="h-full rounded-full bg-indigo-600 dark:bg-indigo-500" style={{ width: `${share}%` }} />
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      <WeakTopics result={result} />

      <section className="mt-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-semibold">Correção</h2>
          <div className="inline-flex rounded-[10px] border border-slate-200 bg-white p-1 text-sm dark:border-slate-800 dark:bg-slate-900">
            {(
              [
                ["todas", "Todas"],
                ["erradas", `Erradas (${result.wrong})`],
                ["branco", `Em branco (${result.blank})`],
              ] as const
            ).map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setFilter(value)}
                aria-pressed={filter === value}
                className={`rounded-md px-3 py-1.5 font-medium transition ${
                  filter === value
                    ? "bg-indigo-600 text-white dark:bg-indigo-500"
                    : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        {shown.length === 0 ? (
          <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">Nenhuma questão aqui.</p>
        ) : (
          <ul className="mt-4 space-y-4">
            {shown.map((question) => (
              <li
                key={question.id}
                className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900"
              >
                <ReviewedQuestion question={question} position={simulation.questions.indexOf(question) + 1} />
                <ReportQuestion simulationId={simulation.id} question={question} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

/**
 * Tópicos do conteúdo do cargo onde mais errou (ou deixou em branco): o que revisar primeiro. Só com
 * a prova ligada aos tópicos.
 */
function WeakTopics({ result }: { result: NonNullable<Simulation["result"]> }) {
  const weak = result.byTopic.filter((topic) => topic.wrong + topic.blank > 0).slice(0, 8);
  if (result.byTopic.length === 0) {
    return (
      <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
        Ligue esta prova aos tópicos do seu cargo (passo Provas) para ver em que tópicos você mais errou.
      </p>
    );
  }
  if (weak.length === 0) {
    return null;
  }
  return (
    <section className="mt-4 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="font-semibold">Onde você mais errou</h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">Tópicos do conteúdo do seu cargo para revisar primeiro.</p>
      <ul className="mt-3 divide-y divide-slate-100 dark:divide-slate-800">
        {weak.map(({ topic, total, wrong, blank }) => (
          <li key={`${topic.subject}-${topic.topic}`} className="flex items-start justify-between gap-3 py-2 text-sm">
            <span className="min-w-0">
              <span className="line-clamp-2">{topic.topic}</span>
              <span className="text-xs text-slate-500 dark:text-slate-400">{topic.subject}</span>
            </span>
            <span className="shrink-0 text-right tabular-nums">
              <span className="font-semibold text-red-700 dark:text-red-400">
                {wrong} {wrong === 1 ? "errada" : "erradas"}
              </span>
              {blank > 0 && <span className="text-slate-500 dark:text-slate-400"> · {blank} em branco</span>}
              <span className="block text-xs text-slate-500 dark:text-slate-400">de {total}</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: "WRONG_ANSWER", label: "Gabarito errado" },
  { value: "BROKEN_STATEMENT", label: "Enunciado ou alternativas cortados ou com erro" },
  { value: "OUT_OF_SCOPE", label: "Fora do conteúdo do cargo" },
  { value: "OTHER", label: "Outro problema" },
];

/**
 * "Reportar problema": a questão sai da sua nota e não volta nas práticas; com reportes de várias
 * pessoas, sai para todos.
 */
function ReportQuestion({ simulationId, question }: { simulationId: string; question: SimulationQuestion }) {
  const report = useReportQuestion(simulationId);
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<ReportReason>("WRONG_ANSWER");
  const [comment, setComment] = useState("");

  if (question.reported) {
    return (
      <p className="mt-4 text-xs text-slate-500 dark:text-slate-400">
        Você reportou esta questão: ela ficou fora da sua nota e não volta nas suas práticas.
      </p>
    );
  }
  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="mt-4 inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-800 hover:underline dark:text-slate-400 dark:hover:text-slate-200"
      >
        <Flag className="size-3.5" aria-hidden="true" />
        Reportar problema
      </button>
    );
  }
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        report.mutate({ questionId: question.id, reason, comment: comment.trim() }, { onSuccess: () => setOpen(false) });
      }}
      className="mt-4 grid gap-2 rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50"
    >
      <select
        value={reason}
        onChange={(e) => setReason(e.target.value as ReportReason)}
        className="h-9 rounded-lg border border-slate-200 bg-white px-2 dark:border-slate-700 dark:bg-slate-950"
      >
        {REPORT_REASONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <input
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        maxLength={500}
        placeholder="O que está errado? (opcional)"
        className="h-9 rounded-lg border border-slate-200 bg-white px-2 dark:border-slate-700 dark:bg-slate-950"
      />
      {report.isError && <p className="text-red-700 dark:text-red-300">Não foi possível reportar agora.</p>}
      <div className="flex gap-2">
        <button type="submit" disabled={report.isPending} className={primaryButton}>
          {report.isPending ? "Enviando…" : "Reportar"}
        </button>
        <button type="button" onClick={() => setOpen(false)} className={secondaryButton}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

/** Questão corrigida: a certa em verde, a sua (se errada) em vermelho. */
function ReviewedQuestion({ question, position }: { question: SimulationQuestion; position: number }) {
  const options =
    question.kind === "TRUE_FALSE"
      ? [
          { letter: "C", text: "Certo" },
          { letter: "E", text: "Errado" },
        ]
      : question.options;
  const status = question.reported
    ? ["Reportada · fora da nota", "bg-amber-50 text-amber-800 dark:bg-amber-400/10 dark:text-amber-300"]
    : question.annulled
      ? ["Anulada", "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"]
      : question.correct === true
        ? ["Certa", "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-400"]
        : question.correct === false
          ? ["Errada", "bg-red-50 text-red-700 dark:bg-red-400/10 dark:text-red-400"]
          : [question.answer ? "Em branco" : "Sem gabarito", "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"];

  return (
    <>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <QuestionBody question={question} position={position} />
          {question.topic && (
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
              Tópico: <span className="font-medium text-slate-700 dark:text-slate-300">{question.topic.topic}</span> (
              {question.topic.subject})
            </p>
          )}
        </div>
        <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${status[1]}`}>{status[0]}</span>
      </div>
      <ol className="mt-4 grid gap-2">
        {options.map((option) => {
          const isAnswer = question.answer?.toUpperCase() === option.letter;
          const isChosen = question.chosen === option.letter;
          return (
            <li
              key={option.letter}
              className={`flex items-start gap-3 rounded-xl border px-4 py-3 ${
                isAnswer
                  ? "border-emerald-600 bg-emerald-50 dark:border-emerald-500 dark:bg-emerald-500/10"
                  : isChosen
                    ? "border-red-600 bg-red-50 dark:border-red-500 dark:bg-red-500/10"
                    : "border-slate-200 dark:border-slate-800"
              }`}
            >
              <span
                className={`grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold ${
                  isAnswer
                    ? "bg-emerald-600 text-white dark:bg-emerald-500"
                    : isChosen
                      ? "bg-red-600 text-white dark:bg-red-500"
                      : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
                }`}
              >
                {isAnswer ? <Check className="size-4" aria-hidden="true" /> : isChosen ? <X className="size-4" aria-hidden="true" /> : option.letter}
              </span>
              <span className="pt-0.5">
                {option.text}
                {isChosen && <span className="ml-2 text-xs font-semibold text-slate-500 dark:text-slate-400">sua resposta</span>}
              </span>
            </li>
          );
        })}
      </ol>
      {question.explanation && (
        <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm leading-relaxed dark:bg-slate-800/50">
          <p className="text-xs font-semibold text-slate-500 dark:text-slate-400">Comentário</p>
          <p className="mt-1 whitespace-pre-line">{question.explanation}</p>
        </div>
      )}
    </>
  );
}
