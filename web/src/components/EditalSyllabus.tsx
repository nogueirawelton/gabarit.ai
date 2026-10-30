import { BookOpen, ChevronRight, ClipboardList, LoaderCircle, RotateCw, Sparkles } from "lucide-react";
import { Link, useNavigate } from "react-router";

import {
  ApiError,
  type EditalExam,
  type StudyDocument,
  type SyllabusSubject,
  type TopicFrequency,
  type TopicPractice,
} from "../api";
import { useReadSyllabus } from "../data/study";
import { useGenerateTopic, usePracticeTopic } from "../data/topics";
import { topicPath } from "../topics";

export function positionLabel(position: { name: string; specialty: string | null }) {
  return position.specialty ? `${position.name} – ${position.specialty}` : position.name;
}

// conectivos que não mudam o nome da disciplina
const CONNECTIVES = new Set(["a", "o", "as", "os", "e", "de", "da", "do", "das", "dos", "em", "na", "no", "para", "por"]);

/** Chave do nome: sem acento, caixa, conectivos e plural (a mesma regra do core, EditalOverview.sameSubject). */
function subjectKey(name: string) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((word) => word && !CONNECTIVES.has(word))
    .map((word) => (word.length > 3 && word.endsWith("s") ? word.slice(0, -1) : word))
    .join("");
}

/**
 * Questões da disciplina no quadro de provas, pelo nome ("Conhecimento de Normas" no quadro ×
 * "Conhecimentos de Normas" no conteúdo: um contido no outro vale).
 */
function questionsFor(exam: EditalExam | null, subject: SyllabusSubject) {
  const name = subjectKey(subject.name);
  return (
    exam?.sections.find((section) => {
      const other = subjectKey(section.subject);
      return name !== "" && other !== "" && (other === name || other.includes(name) || name.includes(other));
    })?.questions ?? null
  );
}

const ANSWER_FORMAT = { MULTIPLE_CHOICE: "múltipla escolha", TRUE_FALSE: "certo ou errado" } as const;

const numberFormat = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 });

/** Como é a prova do cargo (quadro de provas da leitura do edital): total, formato e uma linha por disciplina. */
function ExamSummary({ exam }: { exam: EditalExam }) {
  const facts = [
    exam.totalQuestions && `${exam.totalQuestions} questões`,
    exam.answerFormat && ANSWER_FORMAT[exam.answerFormat],
    exam.essay && "com discursiva ou redação",
  ].filter(Boolean);
  return (
    <div className="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800">
      <p className="flex items-center gap-1.5 font-medium">
        <ClipboardList className="size-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Como é a prova
      </p>
      <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
        {[exam.appliesTo, ...facts].join(" · ")}
      </p>
      {exam.sections.length > 0 && (
        <table className="mt-2 w-full overflow-hidden rounded-lg text-left ring-1 ring-slate-200 dark:ring-slate-800">
          <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/50 dark:text-slate-400">
            <tr>
              <th className="px-3 py-1.5 font-medium">Disciplina</th>
              <th className="px-3 py-1.5 text-right font-medium">Questões</th>
              <th className="px-3 py-1.5 text-right font-medium">Peso</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {exam.sections.map((section, index) => (
              <tr key={`${section.subject}-${index}`}>
                <td className="px-3 py-1.5">
                  {section.subject}
                  {section.block && (
                    <span className="block text-xs text-slate-500 dark:text-slate-400">{section.block}</span>
                  )}
                </td>
                <td className="px-3 py-1.5 text-right tabular-nums">{section.questions ?? "–"}</td>
                <td className="px-3 py-1.5 text-right tabular-nums">
                  {section.weight === null ? "–" : numberFormat.format(section.weight)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

/** Disciplinas na ordem do edital, juntas pelo bloco ("Conhecimentos Básicos"...) quando ele agrupa. */
function byGroup(subjects: SyllabusSubject[]) {
  // ordinal: a posição da disciplina no conteúdo (é por ela que a contagem de questões chega)
  const groups: { group: string | null; subjects: (SyllabusSubject & { ordinal: number })[] }[] = [];
  subjects.forEach((subject, ordinal) => {
    const last = groups.at(-1);
    if (last && last.group === subject.group) {
      last.subjects.push({ ...subject, ordinal });
    } else {
      groups.push({ group: subject.group, subjects: [{ ...subject, ordinal }] });
    }
  });
  return groups;
}

/**
 * Praticar o tópico (questões reais das provas relacionadas e geradas) e gerar questões inéditas no
 * estilo da banca (ou de novo, se falhou ou o prompt mudou).
 */
function TopicActions({
  targetId,
  subject,
  topic,
  state,
}: {
  targetId: string;
  subject: number;
  topic: number;
  state: TopicPractice | undefined;
}) {
  const generate = useGenerateTopic(targetId);
  const practice = usePracticeTopic(targetId);
  const navigate = useNavigate();
  const generation = state?.generation;
  const canGenerate = !generation || generation.outdated || (generation.status === "FAILED" && generation.retryable);
  const error = generate.error ?? practice.error;
  const action = "font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";

  return (
    <>
      <span className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs">
        {state && state.practice > 0 && (
          <button
            type="button"
            onClick={() =>
              practice.mutate({ subject, topic }, { onSuccess: (simulation) => void navigate(`/simulados/${simulation.id}`) })
            }
            disabled={practice.isPending}
            className={action}
          >
            {practice.isPending ? "Abrindo…" : `Praticar (${state.practice})`}
          </button>
        )}
        {generation?.status === "PENDING" ? (
          <span className="inline-flex items-center gap-1 text-slate-500 dark:text-slate-400" role="status">
            <LoaderCircle className="size-3 animate-spin" aria-hidden="true" />
            gerando questões…
          </span>
        ) : (
          canGenerate && (
            <button type="button" onClick={() => generate.mutate({ subject, topic })} disabled={generate.isPending} className={action}>
              <Sparkles className="mr-1 inline size-3" aria-hidden="true" />
              {generate.isPending ? "Pedindo…" : generation ? "Gerar de novo" : "Gerar questões"}
            </button>
          )
        )}
        {generation?.status === "FAILED" && !generation.retryable && (
          <span className="text-slate-500 dark:text-slate-400">não deu para gerar questões deste tópico</span>
        )}
        {error && (
          <span role="alert" className="text-red-700 dark:text-red-300">
            {error instanceof ApiError && error.detail ? error.detail : "Não foi possível agora."}
          </span>
        )}
      </span>
    </>
  );
}

/** "caiu 3×": quantas questões das provas relacionadas ligadas caem no tópico. */
function FallsBadge({ count }: { count: number }) {
  return (
    <span className="ml-2 inline-flex rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-semibold whitespace-nowrap text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300">
      caiu {count}×
    </span>
  );
}

/**
 * Conteúdo programático do cargo escolhido no edital: pedir (nada é lido sozinho ao escolher o
 * cargo: cargo errado não gasta leitura), lendo, falhou (com "tentar de novo"), não achado, ou as
 * disciplinas com os tópicos (fechadas; abre uma por vez).
 */
export function EditalSyllabus({
  targetId,
  document,
  exam,
  frequency,
  practice,
}: {
  targetId: string;
  document: StudyDocument;
  /** A prova do cargo no quadro de provas, quando a leitura do edital traz. */
  exam: EditalExam | null;
  /** Questões das provas relacionadas por tópico (as ligadas a este conteúdo). */
  frequency: TopicFrequency[];
  /** Questões para praticar e geração de cada tópico. */
  practice: TopicPractice[];
}) {
  const read = useReadSyllabus(targetId);
  const position = document.position;
  const reading = document.syllabus;

  if (!position) {
    return null;
  }

  const label = positionLabel(position);
  const retry = (text: string) => (
    <button type="button" onClick={() => read.mutate(document.id)} disabled={read.isPending} className={linkButton}>
      <RotateCw className="size-3.5" aria-hidden="true" />
      {read.isPending ? "Pedindo…" : text}
    </button>
  );

  let body;
  if (!reading) {
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>
          Confira se <span className="font-semibold text-slate-800 dark:text-slate-200">{label}</span> é o seu cargo e
          peça a leitura: a IA traz as disciplinas e os tópicos dele no edital.
        </p>
        <button type="button" onClick={() => read.mutate(document.id)} disabled={read.isPending} className={primaryButton}>
          <Sparkles className="size-4" aria-hidden="true" />
          {read.isPending ? "Pedindo…" : "Ler o conteúdo"}
        </button>
      </div>
    );
  } else if (reading.status === "PENDING") {
    body = (
      <p className="flex items-center gap-2 text-slate-600 dark:text-slate-400" role="status">
        <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Lendo o conteúdo de {label}… pode levar alguns minutos.
      </p>
    );
  } else if (reading.status === "FAILED") {
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>Não conseguimos ler o conteúdo deste cargo{reading.retryable ? " agora" : ""}.</p>
        {reading.retryable && <div className="mt-1">{retry("Tentar de novo")}</div>}
      </div>
    );
  } else if (reading.syllabus && !reading.syllabus.positionFound) {
    body = (
      <p className="text-slate-600 dark:text-slate-400">
        Não achamos o conteúdo de {label} neste edital. Confira se o conteúdo programático está no PDF (às vezes
        sai num edital ou anexo separado).
      </p>
    );
  } else if (reading.syllabus) {
    const subjects = reading.syllabus.subjects;
    const topics = subjects.reduce((total, subject) => total + subject.topics.length, 0);
    body = (
      <div>
        {reading.outdated && (
          <p className="mb-3 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-indigo-50 px-3 py-2 text-indigo-900 dark:bg-indigo-500/10 dark:text-indigo-200">
            Saiu uma versão melhor da leitura do conteúdo. {retry("Ler de novo")}
          </p>
        )}
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {label} · {subjects.length} {subjects.length === 1 ? "disciplina" : "disciplinas"} · {topics}{" "}
          {topics === 1 ? "tópico" : "tópicos"}
        </p>
        <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
          Abra uma disciplina para gerar questões no estilo da banca ou praticar cada tópico.
        </p>
        <div className="mt-2 space-y-3">
          {byGroup(subjects).map(({ group, subjects: inGroup }, index) => (
            <section key={`${group}-${index}`}>
              {group && (
                <h4 className="mb-1 text-xs font-semibold tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  {group}
                </h4>
              )}
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                {inGroup.map((subject) => {
                  const falls = frequency.filter((f) => f.subjectOrdinal === subject.ordinal);
                  const fell = falls.reduce((total, f) => total + f.questions, 0);
                  return (
                    <li key={`${subject.name}-${subject.ordinal}`}>
                      <details className="group">
                        <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 [&::-webkit-details-marker]:hidden">
                          <ChevronRight
                            className="size-4 shrink-0 text-slate-400 transition group-open:rotate-90"
                            aria-hidden="true"
                          />
                          <span className="min-w-0 flex-1 font-medium">{subject.name}</span>
                          <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                            {[
                              questionsFor(exam, subject) !== null && `${questionsFor(exam, subject)} questões`,
                              `${subject.topics.length} ${subject.topics.length === 1 ? "tópico" : "tópicos"}`,
                              fell > 0 && `caiu ${fell}× nas provas`,
                            ]
                              .filter(Boolean)
                              .join(" · ")}
                          </span>
                        </summary>
                        <ol className="list-decimal space-y-1 pr-3 pb-3 pl-12 text-slate-700 marker:text-slate-400 dark:text-slate-300">
                          {subject.topics.map((topic, topicIndex) => {
                            const count = falls.find((f) => f.topicOrdinal === topicIndex)?.questions ?? 0;
                            return (
                              <li key={topicIndex}>
                                {/* a página do tópico: resumo e questões */}
                                <Link
                                  to={topicPath(targetId, subject.ordinal, topicIndex)}
                                  className="hover:text-indigo-700 hover:underline dark:hover:text-indigo-300"
                                >
                                  {topic}
                                </Link>
                                {count > 0 && <FallsBadge count={count} />}
                                <TopicActions
                                  targetId={targetId}
                                  subject={subject.ordinal}
                                  topic={topicIndex}
                                  state={practice.find(
                                    (p) => p.subjectOrdinal === subject.ordinal && p.topicOrdinal === topicIndex,
                                  )}
                                />
                              </li>
                            );
                          })}
                        </ol>
                      </details>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
        <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
          Lido por IA a partir do PDF: confira com o edital.
        </p>
      </div>
    );
  }

  return (
    <>
      {exam && <ExamSummary exam={exam} />}
      <div className="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800">
        <p className="mb-2 flex items-center gap-1.5 font-medium">
          <BookOpen className="size-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
          Conteúdo programático
        </p>
        {body}
        {read.isError && (
          <p role="alert" className="mt-2 text-red-700 dark:text-red-300">
            {read.error instanceof ApiError && read.error.detail ? read.error.detail : "Não foi possível pedir agora."}
          </p>
        )}
      </div>
    </>
  );
}

const primaryButton =
  "mt-3 inline-flex h-9 items-center gap-1.5 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";

const linkButton =
  "inline-flex items-center gap-1.5 text-sm font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";
