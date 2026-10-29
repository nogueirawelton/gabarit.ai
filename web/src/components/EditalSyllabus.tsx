import { BookOpen, ChevronRight, LoaderCircle, RotateCw, Sparkles } from "lucide-react";

import { ApiError, type StudyDocument, type SyllabusSubject } from "../api";
import { useReadSyllabus } from "../data/study";

export function positionLabel(position: { name: string; specialty: string | null }) {
  return position.specialty ? `${position.name} – ${position.specialty}` : position.name;
}

/** Disciplinas na ordem do edital, juntas pelo bloco ("Conhecimentos Básicos"...) quando ele agrupa. */
function byGroup(subjects: SyllabusSubject[]) {
  const groups: { group: string | null; subjects: SyllabusSubject[] }[] = [];
  for (const subject of subjects) {
    const last = groups.at(-1);
    if (last && last.group === subject.group) {
      last.subjects.push(subject);
    } else {
      groups.push({ group: subject.group, subjects: [subject] });
    }
  }
  return groups;
}

/**
 * Conteúdo programático do cargo escolhido no edital: pedir (nada é lido sozinho ao escolher o
 * cargo: cargo errado não gasta leitura), lendo, falhou (com "tentar de novo"), não achado, ou as
 * disciplinas com os tópicos (fechadas; abre uma por vez).
 */
export function EditalSyllabus({ targetId, document }: { targetId: string; document: StudyDocument }) {
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
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {label} · {subjects.length} {subjects.length === 1 ? "disciplina" : "disciplinas"} · {topics}{" "}
          {topics === 1 ? "tópico" : "tópicos"}
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
                {inGroup.map((subject, subjectIndex) => (
                  <li key={`${subject.name}-${subjectIndex}`}>
                    <details className="group">
                      <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 [&::-webkit-details-marker]:hidden">
                        <ChevronRight
                          className="size-4 shrink-0 text-slate-400 transition group-open:rotate-90"
                          aria-hidden="true"
                        />
                        <span className="min-w-0 flex-1 font-medium">{subject.name}</span>
                        <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                          {subject.topics.length} {subject.topics.length === 1 ? "tópico" : "tópicos"}
                        </span>
                      </summary>
                      <ol className="list-decimal space-y-1 pr-3 pb-3 pl-12 text-slate-700 marker:text-slate-400 dark:text-slate-300">
                        {subject.topics.map((topic, topicIndex) => (
                          <li key={topicIndex}>{topic}</li>
                        ))}
                      </ol>
                    </details>
                  </li>
                ))}
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
  );
}

const primaryButton =
  "mt-3 inline-flex h-9 items-center gap-1.5 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";

const linkButton =
  "inline-flex items-center gap-1.5 text-sm font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";
