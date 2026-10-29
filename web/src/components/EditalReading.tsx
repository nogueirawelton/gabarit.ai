import { Check, LoaderCircle, RotateCw, Sparkles } from "lucide-react";
import { useState } from "react";

import { ApiError, type EditalOverview, type EditalPosition, type StudyDocument } from "../api";
import { EDUCATION_LEVELS } from "../brazil";
import { useReadEdital, useStudyTarget, useUpdateStudyTarget } from "../data/study";

const dateFormat = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });

function formatDate(iso: string | null) {
  return iso ? dateFormat.format(new Date(`${iso}T00:00:00Z`)) : null;
}

function positionLabel(position: EditalPosition) {
  return position.specialty ? `${position.name} – ${position.specialty}` : position.name;
}

/**
 * Leitura do edital pela IA, embaixo do arquivo: lendo, falhou (com "tentar de novo") ou pronta, com
 * órgão, banca, datas e cargos para a pessoa conferir e aplicar no concurso. Nada é aplicado sozinho.
 */
export function EditalReading({ targetId, document }: { targetId: string; document: StudyDocument }) {
  const read = useReadEdital(targetId);
  const reading = document.reading;

  if (document.status !== "READY") {
    return null;
  }

  const retry = (label: string) => (
    <button type="button" onClick={() => read.mutate(document.id)} disabled={read.isPending} className={linkButton}>
      <RotateCw className="size-3.5" aria-hidden="true" />
      {read.isPending ? "Pedindo…" : label}
    </button>
  );

  let body;
  if (!reading) {
    body = (
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-slate-600 dark:text-slate-400">
        Este edital ainda não foi lido. {retry("Ler o edital")}
      </p>
    );
  } else if (reading.status === "PENDING") {
    body = (
      <p className="flex items-center gap-2 text-slate-600 dark:text-slate-400" role="status">
        <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Lendo o edital… pode levar alguns minutos. Você pode sair desta página.
      </p>
    );
  } else if (reading.status === "FAILED") {
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>Não conseguimos ler este edital{reading.retryable ? " agora" : ""}.</p>
        {reading.retryable ? (
          <div className="mt-1">{retry("Tentar de novo")}</div>
        ) : (
          <p className="mt-1 text-xs">Confira se o PDF tem texto (não é só imagem escaneada) ou envie outro arquivo.</p>
        )}
      </div>
    );
  } else if (reading.overview) {
    body = <Overview targetId={targetId} overview={reading.overview} />;
  }

  return (
    <div className="border-t border-slate-100 px-4 py-3 text-sm dark:border-slate-800">
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
        <Sparkles className="size-3.5" aria-hidden="true" />
        Leitura do edital
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

function Overview({ targetId, overview }: { targetId: string; overview: EditalOverview }) {
  const target = useStudyTarget(targetId);
  const update = useUpdateStudyTarget(targetId);
  const [applied, setApplied] = useState<string | null>(null);

  const registration =
    overview.registrationStart || overview.registrationEnd
      ? [formatDate(overview.registrationStart), formatDate(overview.registrationEnd)].filter(Boolean).join(" a ")
      : null;
  const facts = [
    ["Órgão", overview.organ],
    ["Banca", overview.board],
    ["Ano", overview.year?.toString() ?? null],
    ["Inscrições", registration],
    ["Prova", formatDate(overview.examDate) ?? "data não marcada no edital"],
  ].filter((fact): fact is [string, string] => Boolean(fact[1]));

  /** Só o que o edital trouxe substitui o que está no concurso; o título e o resto ficam. */
  function apply(positionName?: string) {
    const current = target.data;
    if (!current) {
      return;
    }
    update.mutate(
      {
        title: current.title,
        organ: overview.organ ?? current.organ,
        board: overview.board ?? current.board,
        year: overview.year ?? current.year,
        examDate: overview.examDate ?? current.examDate,
        positionName: positionName ?? current.positionName,
      },
      { onSuccess: () => setApplied(positionName ?? "dados") },
    );
  }

  const currentPosition = target.data?.positionName;

  return (
    <div>
      <dl className="grid gap-x-6 gap-y-1.5 sm:grid-cols-[auto_1fr]">
        {facts.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
            <dd className="font-medium">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={() => apply()}
          disabled={update.isPending || !target.data}
          className={applyButton}
        >
          Usar no concurso
        </button>
        {applied === "dados" && (
          <span className="flex items-center gap-1 text-emerald-700 dark:text-emerald-300" role="status">
            <Check className="size-4" aria-hidden="true" /> Órgão, banca, ano e data atualizados
          </span>
        )}
      </div>

      {overview.positions.length > 0 && (
        <div className="mt-5">
          <p className="font-medium">
            {overview.positions.length} {overview.positions.length === 1 ? "cargo" : "cargos"} no edital
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400">Escolha o seu para montarmos o conteúdo dele.</p>
          <ul className="mt-2 divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
            {overview.positions.map((position, index) => {
              const label = positionLabel(position);
              const chosen = currentPosition === label;
              const details = [
                EDUCATION_LEVELS.find((level) => level.value === position.educationLevel)?.label,
                position.openings && `${position.openings} ${position.openings === "1" ? "vaga" : "vagas"}`,
                position.salary,
              ].filter(Boolean);
              return (
                <li key={`${label}-${index}`} className="flex items-center gap-3 px-3 py-2">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{label}</p>
                    {details.length > 0 && (
                      <p className="text-xs text-slate-500 dark:text-slate-400">{details.join(" · ")}</p>
                    )}
                  </div>
                  {chosen ? (
                    <span className="flex shrink-0 items-center gap-1 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                      <Check className="size-4" aria-hidden="true" /> Seu cargo
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => apply(label)}
                      disabled={update.isPending || !target.data}
                      className={`${linkButton} shrink-0`}
                    >
                      Escolher
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
      {update.isError && (
        <p role="alert" className="mt-2 text-red-700 dark:text-red-300">
          Não foi possível atualizar o concurso agora.
        </p>
      )}
      <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
        Lido por IA a partir do PDF: confira com o edital antes de confiar nas datas.
      </p>
    </div>
  );
}

const linkButton =
  "inline-flex items-center gap-1.5 text-sm font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";

const applyButton =
  "inline-flex h-9 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";
