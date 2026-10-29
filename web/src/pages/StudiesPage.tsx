import { BookOpen, CalendarDays, ChevronRight, Plus } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";

import { ApiError, type StudyTargetSummary } from "../api";
import { StatusBadge } from "../components/ExamCard";
import { ExamListSkeleton } from "../components/Skeleton";
import { useAddStudyTarget, useStudyTargets } from "../data/study";
import { formatDate } from "../exams";

/**
 * Carteira: os concursos que a pessoa estuda. Um da lista entra pela página do concurso; um que já
 * passou (para estudar pelas provas dele) entra aqui, só com o título.
 */
export function StudiesPage() {
  const targets = useStudyTargets();
  const [adding, setAdding] = useState(false);

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Estudos</h1>
          <p className="mt-2 max-w-2xl text-slate-600 dark:text-slate-400">
            Os concursos que você está estudando. Pode ser um aberto ou um que já passou: provas
            relacionadas (do mesmo cargo ou banca) servem de base para estudar.
          </p>
        </div>
        {!adding && (
          <button type="button" onClick={() => setAdding(true)} className={primaryButton}>
            <Plus className="size-4" aria-hidden="true" />
            Cadastrar concurso
          </button>
        )}
      </div>

      {adding && <AddCustomForm onCancel={() => setAdding(false)} />}

      {targets.isPending ? (
        <ExamListSkeleton count={2} />
      ) : targets.isError ? (
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">
          Não foi possível carregar seus estudos agora. Recarregue a página.
        </p>
      ) : targets.data.length === 0 ? (
        <Empty onAdd={() => setAdding(true)} />
      ) : (
        <ul className="mt-8 grid gap-4 sm:grid-cols-2">
          {targets.data.map((target) => (
            <li key={target.id}>
              <TargetCard target={target} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/** Concurso fora da lista: só o título; órgão, banca, cargo e data a pessoa completa (ou o edital, depois). */
function AddCustomForm({ onCancel }: { onCancel: () => void }) {
  const add = useAddStudyTarget();
  const navigate = useNavigate();
  const [title, setTitle] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    add.mutate({ title: title.trim() }, { onSuccess: (target) => void navigate(`/estudos/${target.id}`) });
  }

  return (
    <form
      onSubmit={submit}
      className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900"
    >
      <label className="grid gap-1.5 text-sm">
        <span className="font-medium">Qual concurso?</span>
        <span className="text-slate-500 dark:text-slate-400">
          Ex.: "TJ-SP 2023 - Escrevente". O resto você completa depois.
        </span>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          required
          autoFocus
          className="mt-1 h-11 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950"
        />
      </label>
      {add.isError && (
        <p className="mt-2 text-sm text-red-700 dark:text-red-300">
          {add.error instanceof ApiError && add.error.detail ? add.error.detail : "Não foi possível cadastrar agora."}
        </p>
      )}
      <div className="mt-4 flex gap-2">
        <button type="submit" disabled={!title.trim() || add.isPending} className={primaryButton}>
          {add.isPending ? "Cadastrando…" : "Cadastrar"}
        </button>
        <button type="button" onClick={onCancel} className={secondaryButton}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

function TargetCard({ target }: { target: StudyTargetSummary }) {
  const meta = [target.board, target.year].filter(Boolean).join(" · ");
  return (
    <Link
      to={`/estudos/${target.id}`}
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

function Empty({ onAdd }: { onAdd: () => void }) {
  return (
    <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
      <span className="mx-auto grid size-12 place-items-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
        <BookOpen className="size-6" aria-hidden="true" />
      </span>
      <h2 className="mt-4 text-lg font-semibold">Nenhum concurso na carteira</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-slate-600 dark:text-slate-400">
        Escolha um concurso aberto na lista e toque em "Estudar para este concurso", ou cadastre um que já passou para
        estudar pelas provas dele.
      </p>
      <div className="mt-5 flex flex-wrap justify-center gap-2">
        <Link to="/" className={secondaryButton}>
          Ver concursos abertos
        </Link>
        <button type="button" onClick={onAdd} className={primaryButton}>
          Cadastrar concurso
        </button>
      </div>
    </div>
  );
}

export const primaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-indigo-500 dark:hover:bg-indigo-400";

export const secondaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";
