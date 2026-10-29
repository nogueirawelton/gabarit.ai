import { ArrowLeft, BookOpen, ExternalLink } from "lucide-react";
import { type ReactNode, useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router";

import { ApiError, isUuid } from "../api";
import { displayPositionName } from "../brazil";
import { StatusBadge, UfBadge } from "../components/ExamCard";
import { ExamDetailSkeleton } from "../components/Skeleton";
import { useExam } from "../data/exams";
import { useAddStudyTarget } from "../data/study";
import { registrationPeriod } from "../exams";

/** Detalhe de um concurso: datas, vagas, cargos e o link para a fonte (edital, inscrição). */
export function ExamPage() {
  const id = useParams().id ?? "";
  const exam = useExam(id);

  useEffect(() => {
    if (exam.data) {
      document.title = `${exam.data.title} · gabarit.ai`;
      return () => {
        document.title = "gabarit.ai";
      };
    }
  }, [exam.data]);

  if (!isUuid(id) || (exam.error instanceof ApiError && exam.error.status === 404)) {
    return <NotFound />;
  }
  if (exam.isPending) {
    return <ExamDetailSkeleton />;
  }
  if (exam.isError) {
    return (
      <>
        <BackLink />
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">
          Não foi possível carregar o concurso agora. Recarregue a página.
        </p>
      </>
    );
  }

  const data = exam.data;
  return (
    <>
      <BackLink />

      <div className="mt-6 flex flex-wrap items-center gap-2">
        <StatusBadge exam={data} />
        <UfBadge uf={data.uf} />
        {data.registrationNote && (
          <span className="text-sm font-medium text-slate-500 dark:text-slate-400">{data.registrationNote}</span>
        )}
      </div>
      <h1 className="mt-3 text-2xl font-bold tracking-tight text-balance sm:text-3xl">{data.title}</h1>

      <dl className="mt-8 grid gap-px overflow-hidden rounded-2xl border border-slate-200 bg-slate-200 sm:grid-cols-2 lg:grid-cols-4 dark:border-slate-800 dark:bg-slate-800">
        <Fact label="Inscrições">{registrationPeriod(data.registrationStart, data.registrationEnd)}</Fact>
        <Fact label="Vagas e salário">{data.openings ?? "Não informado"}</Fact>
        <Fact label="Escolaridade">{data.educationLevel ?? "Não informada"}</Fact>
        <Fact label="Local">{data.uf ? [data.uf, data.region].filter(Boolean).join(" · ") : "Nacional"}</Fact>
      </dl>

      <div className="mt-6 flex flex-wrap gap-2">
        <StudyButton examId={data.id} />
        <a
          href={data.url}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex h-11 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-5 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600"
        >
          Ver edital e inscrição
          <ExternalLink className="size-4" aria-hidden="true" />
        </a>
      </div>
      <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
        O edital abre na página do concurso no PCI Concursos, de onde vêm estes dados.
      </p>

      <section className="mt-10 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
        <h2 className="text-lg font-semibold">
          Cargos <span className="font-normal text-slate-500 dark:text-slate-400">({data.positions.length})</span>
        </h2>
        {data.positions.length === 0 ? (
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">A fonte não listou os cargos deste concurso.</p>
        ) : (
          <ul className="mt-4 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-3">
            {data.positions.map((position) => (
              <li key={position.id} className="flex gap-2">
                <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-indigo-400" />
                {displayPositionName(position.name)}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

/** Põe o concurso na carteira (ou abre, se já estava) e leva para a página de estudo dele. */
function StudyButton({ examId }: { examId: string }) {
  const add = useAddStudyTarget();
  const navigate = useNavigate();
  return (
    <div>
      <button
        type="button"
        onClick={() => add.mutate({ examId }, { onSuccess: (target) => void navigate(`/meus-concursos/${target.id}`) })}
        disabled={add.isPending}
        className="inline-flex h-11 items-center gap-2 rounded-[10px] bg-indigo-600 px-5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400"
      >
        <BookOpen className="size-4" aria-hidden="true" />
        {add.isPending ? "Adicionando…" : "Adicionar aos meus concursos"}
      </button>
      {add.isError && (
        <p className="mt-2 text-sm text-red-700 dark:text-red-300">
          {add.error instanceof ApiError && add.error.detail ? add.error.detail : "Não foi possível adicionar agora."}
        </p>
      )}
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="bg-white p-5 dark:bg-slate-900">
      <dt className="text-sm text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}

function BackLink() {
  return (
    <Link
      to="/"
      className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Concursos
    </Link>
  );
}

function NotFound() {
  return (
    <>
      <BackLink />
      <h1 className="mt-6 text-2xl font-bold tracking-tight">Concurso não encontrado</h1>
      <p className="mt-2 text-slate-600 dark:text-slate-400">O link pode estar errado ou o concurso foi removido.</p>
    </>
  );
}
