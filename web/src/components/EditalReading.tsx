import { Check, LoaderCircle, RotateCw, Sparkles } from "lucide-react";
import { useState } from "react";

import { ApiError, type EditalOverview, type StudyDocument } from "../api";
import { EDUCATION_LEVELS } from "../brazil";
import { useChooseEditalPosition, useReadEdital, useStudyTarget, useUpdateStudyTarget } from "../data/study";
import { positionLabel } from "./EditalSyllabus";

const dateFormat = new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" });

function formatDate(iso: string | null) {
  return iso ? dateFormat.format(new Date(`${iso}T00:00:00Z`)) : null;
}

/**
 * Leitura do edital pela IA, embaixo do arquivo (passo "Edital"): pedir, lendo, falhou (com "tentar
 * de novo") ou pronta, com órgão, banca e datas para conferir e aplicar no concurso. Os cargos ficam
 * no passo seguinte ({@link EditalPositions}). Nada é aplicado sozinho.
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
    // nada é lido sozinho no envio: arquivo errado não gasta leitura
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>
          Confira se este é o edital certo (dá para abrir o PDF acima) e peça a leitura: a IA preenche órgão, banca,
          datas e a lista de cargos para você escolher o seu.
        </p>
        <button
          type="button"
          onClick={() => read.mutate(document.id)}
          disabled={read.isPending}
          className={`${applyButton} mt-3 gap-1.5`}
        >
          <Sparkles className="size-4" aria-hidden="true" />
          {read.isPending ? "Pedindo…" : "Ler o edital"}
        </button>
      </div>
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
  const [applied, setApplied] = useState(false);

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

  /** Só o que o edital trouxe substitui o que está no concurso; o título e o cargo ficam. */
  function apply() {
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
        positionName: current.positionName,
      },
      { onSuccess: () => setApplied(true) },
    );
  }

  return (
    <div>
      <dl className="grid gap-x-6 gap-y-1.5 sm:grid-cols-[auto_1fr]">
        {facts.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
            <dd className="font-medium">{value}</dd>
          </div>
        ))}
        <div className="contents">
          <dt className="text-slate-500 dark:text-slate-400">Cargos</dt>
          <dd className="font-medium">{overview.positions.length}</dd>
        </div>
      </dl>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button type="button" onClick={() => apply()} disabled={update.isPending || !target.data} className={applyButton}>
          Usar no concurso
        </button>
        {applied && (
          <span className="flex items-center gap-1 text-emerald-700 dark:text-emerald-300" role="status">
            <Check className="size-4" aria-hidden="true" /> Órgão, banca, ano e data atualizados
          </span>
        )}
      </div>
      {update.isError && (
        <p role="alert" className="mt-2 text-red-700 dark:text-red-300">
          {update.error instanceof ApiError && update.error.detail ? update.error.detail : "Não foi possível salvar agora."}
        </p>
      )}
      <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
        Lido por IA a partir do PDF: confira com o edital antes de confiar nas datas.
      </p>
    </div>
  );
}

/**
 * Cargos da leitura do edital (passo "Cargo"): escolher um pede o conteúdo programático dele. O
 * cargo do edital é guardado à parte do cargo do concurso (que, num concurso da lista, é o nome
 * coletado): é por ele que o conteúdo é lido.
 */
export function EditalPositions({
  targetId,
  document,
  overview,
}: {
  targetId: string;
  document: StudyDocument;
  overview: EditalOverview;
}) {
  const choose = useChooseEditalPosition(targetId);
  const chosenPosition = document.position;

  if (overview.positions.length === 0) {
    return (
      <p className="text-sm text-slate-600 dark:text-slate-400">
        A leitura não achou cargos neste edital. Confira se o PDF é o edital completo (com o quadro de vagas).
      </p>
    );
  }

  return (
    <div className="text-sm">
      <p className="font-medium">
        {overview.positions.length} {overview.positions.length === 1 ? "cargo" : "cargos"} no edital
      </p>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {chosenPosition ? (
          <>
            Seu cargo neste edital:{" "}
            <span className="font-semibold text-slate-700 dark:text-slate-300">{positionLabel(chosenPosition)}</span>
          </>
        ) : (
          "Escolha o seu para montarmos o conteúdo dele."
        )}
      </p>
      {/* lista longa (editais com dezenas de cargos): rola dentro, a página não cresce */}
      <ul className="scroll-thin mt-2 max-h-80 divide-y divide-slate-100 overflow-y-auto overscroll-contain rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
        {overview.positions.map((position, index) => {
          const label = positionLabel(position);
          const chosen = chosenPosition?.name === position.name && chosenPosition.specialty === position.specialty;
          const details = [
            EDUCATION_LEVELS.find((level) => level.value === position.educationLevel)?.label,
            position.openings && `${position.openings} ${position.openings === "1" ? "vaga" : "vagas"}`,
            position.salary,
          ].filter(Boolean);
          return (
            <li key={`${label}-${index}`} className="flex items-center gap-3 px-3 py-2">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{label}</p>
                {details.length > 0 && <p className="text-xs text-slate-500 dark:text-slate-400">{details.join(" · ")}</p>}
              </div>
              {chosen ? (
                <span className="flex shrink-0 items-center gap-1 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                  <Check className="size-4" aria-hidden="true" /> Seu cargo
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => choose.mutate({ documentId: document.id, name: position.name, specialty: position.specialty })}
                  disabled={choose.isPending}
                  className={`${linkButton} shrink-0`}
                >
                  Escolher
                </button>
              )}
            </li>
          );
        })}
      </ul>
      {choose.isError && (
        <p role="alert" className="mt-2 text-red-700 dark:text-red-300">
          {choose.error instanceof ApiError && choose.error.detail ? choose.error.detail : "Não foi possível salvar agora."}
        </p>
      )}
    </div>
  );
}

const linkButton =
  "inline-flex items-center gap-1.5 text-sm font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";

const applyButton =
  "inline-flex h-9 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";
