import { ArrowLeft, ArrowRight, BookOpen, ListChecks, LoaderCircle, Sparkles } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router";

import { ApiError, isUuid } from "../api";
import { ExamDetailSkeleton } from "../components/Skeleton";
import { TopicSummaryPanel } from "../components/TopicSummary";
import { useStudyMaterials, useStudyTarget } from "../data/study";
import { useGenerateTopic, usePracticeTopic, useTopicPractice, useTopics } from "../data/topics";
import { shortTopic, topicPath } from "../topics";

/**
 * Um tópico do conteúdo do cargo: o resumo pela IA e as questões sobre ele (das provas relacionadas
 * e geradas), para praticar. Chega-se pelo plano (os tópicos de cada sessão) ou pelo passo Cargo.
 */
export function TopicPage() {
  const params = useParams();
  const id = params.id ?? "";
  const subject = Number(params.subject);
  const topic = Number(params.topic);
  const target = useStudyTarget(id);
  const materials = useStudyMaterials(id);

  if (!isUuid(id) || !Number.isInteger(subject) || !Number.isInteger(topic)) {
    return <h1 className="text-2xl font-bold tracking-tight">Tópico não encontrado</h1>;
  }
  if (target.isPending || materials.isPending) {
    return <ExamDetailSkeleton />;
  }
  if (target.isError || materials.isError) {
    return <p className="text-sm text-red-700 dark:text-red-300">Não foi possível carregar agora. Recarregue a página.</p>;
  }

  // o conteúdo do cargo: do edital com o cargo escolhido
  const syllabus = materials.data.editais.find((edital) => edital.position && edital.syllabus?.syllabus)?.syllabus?.syllabus;
  const subjects = syllabus?.subjects ?? [];
  const text = subjects[subject]?.topics[topic];
  const back = (
    <Link
      to={`/meus-concursos/${id}?passo=cargo`}
      className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      {target.data.title}
    </Link>
  );
  if (text === undefined) {
    return (
      <>
        {back}
        <h1 className="mt-6 text-2xl font-bold tracking-tight">Tópico não encontrado no conteúdo do seu cargo</h1>
      </>
    );
  }

  // anterior e próximo na ordem do conteúdo (atravessando as disciplinas)
  const flat = subjects.flatMap((s, si) => s.topics.map((t, ti) => ({ s: si, t: ti, text: t })));
  const here = flat.findIndex((entry) => entry.s === subject && entry.t === topic);
  const previous = flat[here - 1];
  const next = flat[here + 1];

  return (
    <>
      {back}
      <p className="mt-6 text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
        {subjects[subject].name}
      </p>
      <h1 className="mt-1 text-2xl font-bold tracking-tight text-balance">{shortTopic(text)}</h1>
      {shortTopic(text) !== text && <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">{text}</p>}
      <Falls targetId={id} subject={subject} topic={topic} />

      <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="flex items-center gap-2 font-semibold">
          <BookOpen className="size-5 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
          Resumo
        </h2>
        <div className="mt-3">
          <TopicSummaryPanel targetId={id} subject={subject} topic={topic} />
        </div>
      </section>

      <Questions targetId={id} subject={subject} topic={topic} />

      <nav aria-label="Outros tópicos" className="mt-8 grid gap-3 sm:grid-cols-2">
        {previous ? (
          <TopicLink targetId={id} entry={previous} label="Tópico anterior" direction="previous" />
        ) : (
          <span />
        )}
        {next && <TopicLink targetId={id} entry={next} label="Próximo tópico" direction="next" />}
      </nav>
    </>
  );
}

/** "Caiu 3× nas provas relacionadas" (só com provas ligadas aos tópicos). */
function Falls({ targetId, subject, topic }: { targetId: string; subject: number; topic: number }) {
  const topics = useTopics(targetId);
  const count =
    topics.data?.frequency.find((f) => f.subjectOrdinal === subject && f.topicOrdinal === topic)?.questions ?? 0;
  if (count === 0) {
    return null;
  }
  return (
    <p className="mt-3 inline-flex rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300">
      Caiu {count}× nas provas relacionadas
    </p>
  );
}

/**
 * Questões do tópico: praticar as que há (das provas relacionadas e geradas) e gerar inéditas no
 * estilo da banca.
 */
function Questions({ targetId, subject, topic }: { targetId: string; subject: number; topic: number }) {
  const practiceState = useTopicPractice(targetId);
  const generate = useGenerateTopic(targetId);
  const practice = usePracticeTopic(targetId);
  const navigate = useNavigate();
  const state = practiceState.data?.find((p) => p.subjectOrdinal === subject && p.topicOrdinal === topic);
  const generation = state?.generation;
  const count = state?.practice ?? 0;
  const unseen = state?.unseen ?? 0;
  const generating = generation?.status === "PENDING";
  // falha que não adianta repetir (mesmo pedido, mesma resposta): só com prompt novo
  const stuck = generation?.status === "FAILED" && !generation.retryable && !generation.outdated;
  const error = generate.error ?? practice.error;

  return (
    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="flex items-center gap-2 font-semibold">
        <ListChecks className="size-5 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Questões
      </h2>
      <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
        {count > 0
          ? `${count} ${count === 1 ? "questão" : "questões"} sobre este tópico (${unseen} que você ainda não viu), das provas relacionadas e geradas pela IA no estilo da banca. Cada prática traz ${PRACTICE_SIZE}, as não vistas primeiro.`
          : "Ainda não há questões deste tópico. Gere questões inéditas no estilo da banca, ou ligue provas relacionadas aos tópicos no passo Provas."}
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        {count > 0 && (
          <button
            type="button"
            onClick={() =>
              practice.mutate({ subject, topic }, { onSuccess: (simulation) => void navigate(`/simulados/${simulation.id}`) })
            }
            disabled={practice.isPending}
            className={primaryButton}
          >
            <ListChecks className="size-4" aria-hidden="true" />
            {practice.isPending ? "Abrindo…" : `Praticar (${Math.min(count, PRACTICE_SIZE)})`}
          </button>
        )}
        {generating ? (
          <span className="inline-flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400" role="status">
            <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
            Gerando questões…
          </span>
        ) : (
          !stuck && (
            <button
              type="button"
              onClick={() => generate.mutate({ subject, topic })}
              disabled={generate.isPending}
              // sem questão não vista, gerar mais é o próximo passo
              className={count > 0 && unseen > 0 ? secondaryButton : primaryButton}
            >
              <Sparkles className="size-4" aria-hidden="true" />
              {generate.isPending
                ? "Pedindo…"
                : generation?.status === "FAILED"
                  ? "Tentar de novo"
                  : generation
                    ? `Gerar mais ${PRACTICE_SIZE}`
                    : `Gerar ${PRACTICE_SIZE} questões`}
            </button>
          )
        )}
      </div>
      {stuck && (
        <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">Não deu para gerar questões deste tópico.</p>
      )}
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-700 dark:text-red-300">
          {error instanceof ApiError && error.detail ? error.detail : "Não foi possível agora."}
        </p>
      )}
    </section>
  );
}

function TopicLink({
  targetId,
  entry,
  label,
  direction,
}: {
  targetId: string;
  entry: { s: number; t: number; text: string };
  label: string;
  direction: "previous" | "next";
}) {
  return (
    <Link
      to={topicPath(targetId, entry.s, entry.t)}
      className={`group flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 transition hover:border-indigo-300 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-indigo-500/50 ${
        direction === "next" ? "flex-row-reverse text-right" : ""
      }`}
    >
      {direction === "previous" ? (
        <ArrowLeft className="size-4 shrink-0 text-slate-400" aria-hidden="true" />
      ) : (
        <ArrowRight className="size-4 shrink-0 text-slate-400" aria-hidden="true" />
      )}
      <span className="min-w-0">
        <span className="block text-xs text-slate-500 dark:text-slate-400">{label}</span>
        <span className="block truncate text-sm font-medium group-hover:text-indigo-700 dark:group-hover:text-indigo-300">
          {shortTopic(entry.text)}
        </span>
      </span>
    </Link>
  );
}

/** Questões por prática e por geração (no core: MAX_PRACTICE e PER_TOPIC). */
const PRACTICE_SIZE = 10;

const primaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";

const secondaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 transition hover:border-slate-400 disabled:opacity-60 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";
