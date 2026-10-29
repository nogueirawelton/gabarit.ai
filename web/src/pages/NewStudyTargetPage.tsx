import { ArrowLeft, ArrowRight, MapPin, PencilLine, Search } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";

import { ApiError, type ExamSummary } from "../api";
import { StatusBadge } from "../components/ExamCard";
import { useExamList } from "../data/exams";
import { useAddStudyTarget, useStudyTargets } from "../data/study";
import { useDebounced } from "../useDebounced";
import { primaryButton, secondaryButton } from "./StudiesPage";

/**
 * Passo 1 de um concurso novo: escolher um da nossa lista (já vem com título, órgão, ano e cargos) ou
 * cadastrar à mão (concurso que já passou ou que não está na lista: só o título). Depois segue o passo
 * a passo na página do concurso.
 */
export function NewStudyTargetPage() {
  const [manual, setManual] = useState(false);

  return (
    <>
      <Link
        to="/meus-concursos"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Meus concursos
      </Link>
      <p className="mt-6 text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
        Passo 1 de 5
      </p>
      <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Qual concurso você vai estudar?</h1>
      <p className="mt-2 max-w-2xl text-slate-600 dark:text-slate-400">
        Procure na nossa lista de concursos abertos. Se for um que já passou ou não está na lista, cadastre à mão: o
        edital completa o resto no próximo passo.
      </p>

      {manual ? <ManualForm onCancel={() => setManual(false)} /> : <ExamSearch onManual={() => setManual(true)} />}
    </>
  );
}

function ExamSearch({ onManual }: { onManual: () => void }) {
  const [query, setQuery] = useState("");
  const q = useDebounced(query.trim());
  const exams = useExamList("all", q, q.length >= 2);
  const results = exams.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <label className="relative block">
        <span className="sr-only">Buscar concurso</span>
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Órgão, cidade ou cargo (ex.: TJ-SP, Prefeitura de Santos)"
          autoFocus
          className={`${inputClass} pl-9`}
        />
      </label>

      {q.length < 2 ? (
        <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">Digite pelo menos 2 letras.</p>
      ) : exams.isPending ? (
        <p className="mt-4 text-sm text-slate-500 dark:text-slate-400" role="status">
          Buscando…
        </p>
      ) : exams.isError ? (
        <p className="mt-4 text-sm text-red-700 dark:text-red-300">Não foi possível buscar agora.</p>
      ) : results.length === 0 ? (
        <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">Nenhum concurso aberto com "{q}".</p>
      ) : (
        <ul className="scroll-thin mt-4 max-h-[28rem] divide-y divide-slate-100 overflow-y-auto overscroll-contain rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {results.map((exam) => (
            <li key={exam.id}>
              <ExamOption exam={exam} />
            </li>
          ))}
          {exams.hasNextPage && (
            <li className="p-3 text-center">
              <button
                type="button"
                onClick={() => void exams.fetchNextPage()}
                disabled={exams.isFetchingNextPage}
                className="text-sm font-semibold text-indigo-700 hover:underline dark:text-indigo-300"
              >
                {exams.isFetchingNextPage ? "Carregando…" : "Carregar mais"}
              </button>
            </li>
          )}
        </ul>
      )}

      <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-slate-100 pt-5 dark:border-slate-800">
        <p className="text-sm text-slate-600 dark:text-slate-400">Já passou ou não está na lista?</p>
        <button type="button" onClick={onManual} className={secondaryButton}>
          <PencilLine className="size-4" aria-hidden="true" />
          Cadastrar à mão
        </button>
      </div>
    </section>
  );
}

/** Um concurso da busca: escolher põe nos meus concursos; se já está, abre o que existe. */
function ExamOption({ exam }: { exam: ExamSummary }) {
  const add = useAddStudyTarget();
  const targets = useStudyTargets();
  const navigate = useNavigate();
  const existing = targets.data?.find((target) => target.examId === exam.id);

  return (
    <div className="flex flex-wrap items-center gap-3 px-4 py-3">
      <div className="min-w-0 flex-1">
        <p className="font-medium">{exam.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
          <StatusBadge exam={exam} />
          <span className="inline-flex items-center gap-1">
            <MapPin className="size-3.5" aria-hidden="true" />
            {exam.uf ?? "Nacional"}
          </span>
        </div>
      </div>
      {existing ? (
        <Link to={`/meus-concursos/${existing.id}`} className={smallSecondaryButton}>
          Já está nos seus concursos
          <ArrowRight className="size-4" aria-hidden="true" />
        </Link>
      ) : (
        <button
          type="button"
          onClick={() =>
            add.mutate({ examId: exam.id }, { onSuccess: (target) => void navigate(`/meus-concursos/${target.id}`) })
          }
          disabled={add.isPending}
          className={smallPrimaryButton}
        >
          {add.isPending ? "Adicionando…" : "Escolher"}
        </button>
      )}
      {add.isError && (
        <p role="alert" className="w-full text-sm text-red-700 dark:text-red-300">
          {add.error instanceof ApiError && add.error.detail ? add.error.detail : "Não foi possível adicionar agora."}
        </p>
      )}
    </div>
  );
}

/** Concurso fora da lista: só o título; órgão, banca, cargo e data vêm do edital (ou à mão) depois. */
function ManualForm({ onCancel }: { onCancel: () => void }) {
  const add = useAddStudyTarget();
  const navigate = useNavigate();
  const [title, setTitle] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    add.mutate({ title: title.trim() }, { onSuccess: (target) => void navigate(`/meus-concursos/${target.id}?passo=edital`) });
  }

  return (
    <form
      onSubmit={submit}
      className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900"
    >
      <label className="grid gap-1.5 text-sm">
        <span className="font-medium">Nome do concurso</span>
        <span className="text-slate-500 dark:text-slate-400">Ex.: "TJ-SP 2023 - Escrevente". O resto vem do edital.</span>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          required
          autoFocus
          className={`${inputClass} mt-1`}
        />
      </label>
      {add.isError && (
        <p className="mt-2 text-sm text-red-700 dark:text-red-300">
          {add.error instanceof ApiError && add.error.detail ? add.error.detail : "Não foi possível cadastrar agora."}
        </p>
      )}
      <div className="mt-6 flex flex-wrap justify-between gap-2">
        <button type="button" onClick={onCancel} className={secondaryButton}>
          <ArrowLeft className="size-4" aria-hidden="true" />
          Buscar na lista
        </button>
        <button type="submit" disabled={!title.trim() || add.isPending} className={primaryButton}>
          {add.isPending ? "Cadastrando…" : "Continuar"}
          <ArrowRight className="size-4" aria-hidden="true" />
        </button>
      </div>
    </form>
  );
}

const inputClass =
  "h-11 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950";

const smallPrimaryButton =
  "inline-flex h-9 shrink-0 items-center gap-1.5 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";

const smallSecondaryButton =
  "inline-flex h-9 shrink-0 items-center gap-1.5 rounded-[10px] border border-slate-200 bg-white px-3.5 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";
