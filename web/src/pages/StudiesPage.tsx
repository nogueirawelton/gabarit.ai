import { BookOpen, CalendarDays, ChevronRight, Plus } from "lucide-react";
import { Link } from "react-router";

import type { StudyTargetSummary } from "../api";
import { StatusBadge } from "../components/ExamCard";
import { StudyAgenda } from "../components/StudyAgenda";
import { ExamListSkeleton } from "../components/Skeleton";
import { useStudyTargets } from "../data/study";
import { formatDate } from "../exams";

/**
 * Meus concursos: os que a pessoa estuda (carteira). Cada um é montado passo a passo: dados,
 * edital, cargo, provas relacionadas e, no fim, o estudo.
 */
export function StudiesPage() {
  const targets = useStudyTargets();

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Meus concursos</h1>
          <p className="mt-2 max-w-2xl text-slate-600 dark:text-slate-400">
            Os concursos que você está estudando. Pode ser um aberto ou um que já passou: provas relacionadas (do mesmo
            cargo ou banca) servem de base para estudar.
          </p>
        </div>
        <Link to="/meus-concursos/novo" className={primaryButton}>
          <Plus className="size-4" aria-hidden="true" />
          Novo concurso
        </Link>
      </div>

      {targets.isPending ? (
        <ExamListSkeleton count={2} />
      ) : targets.isError ? (
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">
          Não foi possível carregar seus concursos agora. Recarregue a página.
        </p>
      ) : targets.data.length === 0 ? (
        <Empty />
      ) : (
        <>
          <ul className="mt-8 grid gap-4 sm:grid-cols-2">
            {targets.data.map((target) => (
              <li key={target.id}>
                <TargetCard target={target} />
              </li>
            ))}
          </ul>
          <div className="mt-8">
            <StudyAgenda targets={targets.data} />
          </div>
        </>
      )}
    </>
  );
}

function TargetCard({ target }: { target: StudyTargetSummary }) {
  const meta = [target.board, target.year].filter(Boolean).join(" · ");
  return (
    <Link
      to={`/meus-concursos/${target.id}`}
      className="group flex h-full flex-col rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-indigo-300 hover:shadow-md hover:shadow-indigo-900/5 focus-visible:border-indigo-600 focus-visible:ring-4 focus-visible:ring-indigo-600/20 focus-visible:outline-none dark:border-slate-800 dark:bg-slate-900 dark:hover:border-indigo-500/50"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="line-clamp-2 font-semibold leading-snug group-hover:text-indigo-700 dark:group-hover:text-indigo-300">
          {target.title}
        </h2>
        <ChevronRight className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
      </div>
      <p className="mt-1 text-sm">
        {target.positionName ? (
          <span className="text-slate-700 dark:text-slate-300">{target.positionName}</span>
        ) : (
          <span className="text-amber-700 dark:text-amber-300">Escolha o seu cargo</span>
        )}
      </p>
      {meta && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{meta}</p>}
      <div className="mt-auto flex flex-wrap items-center gap-2 pt-4">
        {target.exam && <StatusBadge exam={target.exam} />}
        {target.examDate && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            <CalendarDays className="size-3.5" aria-hidden="true" />
            Prova em {formatDate(target.examDate, true)}
          </span>
        )}
      </div>
    </Link>
  );
}

function Empty() {
  return (
    <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
      <span className="mx-auto grid size-12 place-items-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
        <BookOpen className="size-6" aria-hidden="true" />
      </span>
      <h2 className="mt-4 text-lg font-semibold">Nenhum concurso ainda</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-slate-600 dark:text-slate-400">
        Escolha um concurso da nossa lista ou cadastre um que já passou para estudar pelas provas dele. Depois é passo a
        passo: edital, cargo e provas relacionadas.
      </p>
      <div className="mt-5 flex flex-wrap justify-center gap-2">
        <Link to="/meus-concursos/novo" className={primaryButton}>
          <Plus className="size-4" aria-hidden="true" />
          Novo concurso
        </Link>
      </div>
    </div>
  );
}

export const primaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-indigo-500 dark:hover:bg-indigo-400";

export const secondaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";
