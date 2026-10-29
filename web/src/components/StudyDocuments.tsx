import { Check, FileText, ListChecks, LoaderCircle, Plus, RotateCw, Sparkles, Trash2, Upload } from "lucide-react";
import { type ChangeEvent, type FormEvent, useRef, useState } from "react";

import { ApiError, type StudyDocument, type StudyDocumentKind, type StudyPaper } from "../api";
import {
  MAX_DOCUMENT_BYTES,
  useAddPaper,
  useReadPaper,
  useRemoveDocument,
  useRemovePaper,
  useUploadDocument,
} from "../data/study";
import { useClassifyPaper, useTopics } from "../data/topics";
import { EditalReading } from "./EditalReading";

const sizeFormat = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

function formatSize(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${sizeFormat.format(bytes / 1024 / 1024)} MB`;
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.detail ? error.detail : fallback;
}

// Arquivos do concurso, nos passos "Edital" e "Provas relacionadas". Cada prova relacionada tem o
// espaço da prova e o do gabarito dela, para o par ficar explícito. O PDF vai do navegador direto
// para o armazenamento e o servidor confere (é PDF? abre? tamanho e páginas no limite); recusado
// mostra o motivo.

/** Edital do concurso e retificações, cada um com a leitura pela IA embaixo. */
export function EditalFiles({ targetId, editais }: { targetId: string; editais: StudyDocument[] }) {
  return (
    <div>
      <UploadButton targetId={targetId} kind="EDITAL" label={editais.length > 0 ? "Enviar outro (retificação)" : "Enviar edital"} />
      {editais.length > 0 && (
        <ul className="mt-4 divide-y divide-slate-100 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {editais.map((document) => (
            <li key={document.id}>
              <DocumentRow targetId={targetId} document={document} />
              <EditalReading targetId={targetId} document={document} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Provas de outros concursos do mesmo cargo ou banca, cada uma com gabarito e (opcional) edital. */
export function RelatedPapers({ targetId, papers }: { targetId: string; papers: StudyPaper[] }) {
  const [adding, setAdding] = useState(false);

  return (
    <div>
      {!adding && (
        <button type="button" onClick={() => setAdding(true)} className={smallButton}>
          <Plus className="size-4" aria-hidden="true" />
          Adicionar prova
        </button>
      )}

      {adding && <AddPaperForm targetId={targetId} onDone={() => setAdding(false)} />}

      {papers.length > 0 && (
        <ul className="mt-4 grid gap-3">
          {papers.map((paper) => (
            <li key={paper.id}>
              <PaperCard targetId={targetId} paper={paper} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function AddPaperForm({ targetId, onDone }: { targetId: string; onDone: () => void }) {
  const add = useAddPaper(targetId);
  const [year, setYear] = useState("");
  const [variant, setVariant] = useState("");
  const [answersIncluded, setAnswersIncluded] = useState(false);

  function submit(event: FormEvent) {
    event.preventDefault();
    add.mutate(
      { year: year ? Number(year) : null, variant: variant.trim() || null, answersIncluded },
      { onSuccess: onDone },
    );
  }

  return (
    <form onSubmit={submit} className="mt-3 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="grid gap-1.5 text-sm">
          <span className="text-slate-500 dark:text-slate-400">Ano da prova</span>
          <input
            value={year}
            onChange={(e) => setYear(e.target.value.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            placeholder="Ex.: 2019"
            autoFocus
            className={inputClass}
          />
        </label>
        <label className="grid gap-1.5 text-sm">
          <span className="text-slate-500 dark:text-slate-400">Tipo ou cor da prova (opcional)</span>
          <input
            value={variant}
            onChange={(e) => setVariant(e.target.value)}
            maxLength={30}
            placeholder="Ex.: Tipo 1, Branca"
            className={inputClass}
          />
        </label>
      </div>
      <label className="mt-3 flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={answersIncluded}
          onChange={(e) => setAnswersIncluded(e.target.checked)}
          className="size-4 accent-indigo-600"
        />
        O gabarito está no mesmo arquivo da prova
      </label>
      {add.isError && (
        <p className="mt-2 text-sm text-red-700 dark:text-red-300">{errorMessage(add.error, "Não foi possível adicionar agora.")}</p>
      )}
      <div className="mt-4 flex gap-2">
        <button type="submit" disabled={add.isPending} className={primarySmallButton}>
          {add.isPending ? "Adicionando…" : "Adicionar"}
        </button>
        <button type="button" onClick={onDone} className={smallButton}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

/** Uma prova relacionada: título (ano e tipo) e os espaços: prova, gabarito e (opcional) o edital dela. */
function PaperCard({ targetId, paper }: { targetId: string; paper: StudyPaper }) {
  const remove = useRemovePaper(targetId);
  const title = [paper.year ? `Prova de ${paper.year}` : "Prova (ano não informado)", paper.variant]
    .filter(Boolean)
    .join(" · ");

  function confirmRemove() {
    if (window.confirm(`Remover "${title}" e os arquivos dela?`)) {
      remove.mutate(paper.id);
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
      <div className="flex items-center justify-between gap-3">
        <p className="font-medium">{title}</p>
        <button
          type="button"
          onClick={confirmRemove}
          disabled={remove.isPending}
          aria-label={`Remover ${title}`}
          className="grid size-9 shrink-0 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-red-700 disabled:opacity-50 dark:hover:bg-slate-800 dark:hover:text-red-300"
        >
          <Trash2 className="size-4" aria-hidden="true" />
        </button>
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Slot targetId={targetId} paperId={paper.id} kind="PROVA" label="Prova" document={paper.exam} />
        {paper.answersIncluded ? (
          <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-500 dark:bg-slate-800/50 dark:text-slate-400">
            <p className="font-medium text-slate-700 dark:text-slate-300">Gabarito</p>
            <p className="mt-1">No mesmo arquivo da prova.</p>
          </div>
        ) : (
          <Slot targetId={targetId} paperId={paper.id} kind="GABARITO" label="Gabarito" document={paper.answerKey} />
        )}
        <Slot
          targetId={targetId}
          paperId={paper.id}
          kind="EDITAL"
          label="Edital desta prova"
          hint="Opcional. Sem ele, usamos o edital do concurso."
          uploadLabel="Enviar edital"
          document={paper.edital}
        />
      </div>
      <PaperReadingPanel targetId={targetId} paper={paper} />
    </div>
  );
}

/**
 * Leitura das questões da prova pela IA: pedir (com a prova e o gabarito enviados; nada é lido
 * sozinho), lendo, falhou ou pronta, com o resumo (quantas, anuladas, por disciplina) e os avisos
 * que impedem o simulado de corrigir (gabarito não achado, questões sem resposta).
 */
function PaperReadingPanel({ targetId, paper }: { targetId: string; paper: StudyPaper }) {
  const read = useReadPaper(targetId);
  const reading = paper.reading;
  const filesReady =
    paper.exam?.status === "READY" && (paper.answersIncluded || paper.answerKey?.status === "READY");

  const action = (label: string, primary = false) => (
    <button
      type="button"
      onClick={() => read.mutate(paper.id)}
      disabled={read.isPending}
      className={primary ? `${primarySmallButton} gap-1.5` : linkButton}
    >
      {primary ? <Sparkles className="size-4" aria-hidden="true" /> : <RotateCw className="size-3.5" aria-hidden="true" />}
      {read.isPending ? "Pedindo…" : label}
    </button>
  );

  let body;
  if (!filesReady) {
    body = (
      <p className="text-slate-500 dark:text-slate-400">
        Envie {paper.answersIncluded ? "o PDF da prova" : "a prova e o gabarito"} para lermos as questões.
      </p>
    );
  } else if (!reading) {
    body = (
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <p className="text-slate-600 dark:text-slate-400">
          Confira os arquivos e peça a leitura: a IA separa as questões com as alternativas e a resposta do gabarito.
        </p>
        {action("Ler as questões", true)}
      </div>
    );
  } else if (reading.status === "PENDING") {
    body = (
      <p className="flex items-center gap-2 text-slate-600 dark:text-slate-400" role="status">
        <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Lendo as questões… pode levar alguns minutos.
      </p>
    );
  } else if (reading.status === "FAILED" || !reading.summary) {
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>Não conseguimos ler as questões desta prova{reading.retryable ? " agora" : ""}.</p>
        {reading.retryable ? (
          <div className="mt-1">{action("Tentar de novo")}</div>
        ) : (
          <p className="mt-1 text-xs">Confira se o PDF é a prova completa e legível, ou envie outro arquivo.</p>
        )}
      </div>
    );
  } else {
    const summary = reading.summary;
    const warnings = [
      !summary.answerKeyFound && "Não achamos o gabarito desta prova: o simulado não terá correção.",
      summary.withoutAnswer > 0 &&
        `${summary.withoutAnswer} ${summary.withoutAnswer === 1 ? "questão ficou" : "questões ficaram"} sem resposta no gabarito.`,
      paper.variant &&
        summary.variant &&
        summary.variant.toLowerCase() !== paper.variant.toLowerCase() &&
        `A prova diz "${summary.variant}", mas você informou "${paper.variant}". Confira o tipo.`,
    ].filter((warning): warning is string => Boolean(warning));
    body = (
      <div>
        {reading.outdated && (
          <p className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-indigo-50 px-3 py-2 text-indigo-900 dark:bg-indigo-500/10 dark:text-indigo-200">
            Saiu uma versão melhor da leitura. {action("Ler de novo")}
          </p>
        )}
        <p className="font-medium">
          {summary.questions} {summary.questions === 1 ? "questão" : "questões"}
          {summary.annulled > 0 && (
            <span className="font-normal text-slate-500 dark:text-slate-400">
              {" "}
              · {summary.annulled} {summary.annulled === 1 ? "anulada" : "anuladas"}
            </span>
          )}
        </p>
        {summary.bySubject.length > 0 && (
          <ul className="mt-2 flex flex-wrap gap-1.5">
            {summary.bySubject.map(({ subject, questions }) => (
              <li
                key={subject}
                className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300"
              >
                {subject} · {questions}
              </li>
            ))}
          </ul>
        )}
        {warnings.map((warning) => (
          <p
            key={warning}
            className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-amber-800 dark:bg-amber-400/10 dark:text-amber-300"
          >
            {warning}
          </p>
        ))}
        <PaperTopicsRow targetId={targetId} paperId={paper.id} />
      </div>
    );
  }

  return (
    <div className="mt-3 border-t border-slate-100 pt-3 text-sm dark:border-slate-800">
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
        <ListChecks className="size-3.5" aria-hidden="true" />
        Questões
      </p>
      {body}
      {read.isError && <p className="mt-2 text-red-700 dark:text-red-300">{errorMessage(read.error, "Não foi possível pedir agora.")}</p>}
    </div>
  );
}

/** Espaço de um arquivo: vazio (botão de enviar), com o arquivo, ou recusado (motivo + enviar outro). */
function Slot({
  targetId,
  paperId,
  kind,
  label,
  hint,
  uploadLabel,
  document,
}: {
  targetId: string;
  paperId: string;
  kind: StudyDocumentKind;
  label: string;
  hint?: string;
  uploadLabel?: string;
  document: StudyDocument | null;
}) {
  const filled = document && document.status === "READY";
  return (
    <div
      className={`rounded-lg p-3 ${filled ? "bg-slate-50 dark:bg-slate-800/50" : "border border-dashed border-slate-300 dark:border-slate-700"}`}
    >
      <p className="text-sm font-medium text-slate-700 dark:text-slate-300">{label}</p>
      {hint && !filled && <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{hint}</p>}
      {filled ? (
        <div className="mt-1">
          <DocumentRow targetId={targetId} document={document} compact />
        </div>
      ) : (
        <>
          {document?.status === "REJECTED" && (
            <p className="mt-1 text-xs text-red-700 dark:text-red-300">
              "{document.originalName}" foi recusado: {document.rejectionReason}
            </p>
          )}
          <div className="mt-2">
            <UploadButton
              targetId={targetId}
              kind={kind}
              paperId={paperId}
              label={document?.status === "REJECTED" ? "Enviar outro PDF" : (uploadLabel ?? `Enviar ${label.toLowerCase()}`)}
            />
          </div>
        </>
      )}
    </div>
  );
}

/** Botão que escolhe o PDF e envia (com barra de progresso), já conferindo tipo e tamanho. */
function UploadButton({
  targetId,
  kind,
  paperId,
  label,
}: {
  targetId: string;
  kind: StudyDocumentKind;
  paperId?: string;
  label: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadDocument(targetId);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  function pick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // limpa a seleção: escolher o mesmo arquivo de novo dispara o onChange outra vez
    event.target.value = "";
    if (!file) {
      return;
    }
    setError(null);
    if (file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf")) {
      setError("Envie um arquivo PDF.");
      return;
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      setError("Arquivo grande demais (máximo de 30 MB).");
      return;
    }
    setProgress(0);
    upload.mutate(
      { file, kind, paperId, onProgress: setProgress },
      {
        onSuccess: (document) => {
          // espaço de prova/gabarito mostra o motivo no próprio espaço; edital mostra aqui
          if (document.status === "REJECTED" && !paperId) {
            setError(`"${document.originalName}" foi recusado: ${document.rejectionReason ?? "arquivo inválido"}.`);
          }
        },
        onError: (e) => setError(errorMessage(e, "Não foi possível enviar agora. Tente de novo.")),
        onSettled: () => setProgress(null),
      },
    );
  }

  return (
    <div>
      {progress === null && (
        <button type="button" onClick={() => input.current?.click()} disabled={upload.isPending} className={smallButton}>
          <Upload className="size-4" aria-hidden="true" />
          {label}
        </button>
      )}
      <input
        ref={input}
        type="file"
        accept="application/pdf,.pdf"
        onChange={pick}
        className="hidden"
        tabIndex={-1}
        aria-hidden="true"
      />
      {progress !== null && <UploadProgress fraction={progress} />}
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700 dark:text-red-300">
          {error}
        </p>
      )}
    </div>
  );
}

/** Barra de envio; em 100% o arquivo chegou e o servidor está conferindo. */
function UploadProgress({ fraction }: { fraction: number }) {
  const percent = Math.round(fraction * 100);
  return (
    <div className="min-w-48" role="status">
      <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className="h-full rounded-full bg-indigo-600 transition-[width] dark:bg-indigo-500"
          style={{ width: `${percent}%` }}
        />
      </div>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
        {percent < 100 ? `Enviando… ${percent}%` : "Conferindo o arquivo…"}
      </p>
    </div>
  );
}

function DocumentRow({
  targetId,
  document,
  compact = false,
}: {
  targetId: string;
  document: StudyDocument;
  compact?: boolean;
}) {
  const remove = useRemoveDocument(targetId);
  const details = [
    document.pages ? `${document.pages} ${document.pages === 1 ? "página" : "páginas"}` : null,
    document.sizeBytes ? formatSize(document.sizeBytes) : null,
  ].filter(Boolean);

  function confirmRemove() {
    if (window.confirm(`Remover "${document.originalName}"?`)) {
      remove.mutate(document.id);
    }
  }

  return (
    <div className={`flex items-center gap-3 ${compact ? "" : "px-4 py-3"}`}>
      <FileText
        className={`size-5 shrink-0 ${document.status === "REJECTED" ? "text-red-400" : "text-slate-400"}`}
        aria-hidden="true"
      />
      <div className="min-w-0 flex-1">
        {document.url ? (
          <a
            href={document.url}
            target="_blank"
            rel="noopener noreferrer"
            className="block truncate text-sm font-medium hover:text-indigo-700 hover:underline dark:hover:text-indigo-300"
          >
            {document.originalName}
          </a>
        ) : (
          <p className="truncate text-sm font-medium">{document.originalName}</p>
        )}
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {document.status === "REJECTED" ? (
            <span className="text-red-700 dark:text-red-300">Recusado: {document.rejectionReason}</span>
          ) : document.status === "UPLOADING" ? (
            "Envio não concluído"
          ) : (
            details.join(" · ")
          )}
        </p>
      </div>
      <button
        type="button"
        onClick={confirmRemove}
        disabled={remove.isPending}
        aria-label={`Remover ${document.originalName}`}
        className="grid size-9 shrink-0 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-red-700 disabled:opacity-50 dark:hover:bg-slate-800 dark:hover:text-red-300"
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}

const inputClass =
  "h-10 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950";

const smallButton =
  "inline-flex h-9 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-3.5 text-sm font-semibold text-slate-800 transition hover:border-slate-400 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";

/**
 * Ligação das questões da prova aos tópicos do conteúdo do cargo (o que mais cai): pedir, ligando,
 * falhou ou ligada. Sem o conteúdo do cargo lido, só avisa onde fazer.
 */
function PaperTopicsRow({ targetId, paperId }: { targetId: string; paperId: string }) {
  const topics = useTopics(targetId);
  const classify = useClassifyPaper(targetId);
  if (!topics.data) {
    return null;
  }
  const state = topics.data.papers.find((paper) => paper.paperId === paperId);
  const button = (label: string, primary = false) => (
    <button
      type="button"
      onClick={() => classify.mutate(paperId)}
      disabled={classify.isPending}
      className={primary ? `${smallButton} gap-1.5` : linkButton}
    >
      {primary ? <Sparkles className="size-4" aria-hidden="true" /> : <RotateCw className="size-3.5" aria-hidden="true" />}
      {classify.isPending ? "Pedindo…" : label}
    </button>
  );

  let body;
  if (!topics.data.syllabusReady) {
    body = (
      <p className="text-slate-500 dark:text-slate-400">
        Para ver em que tópicos do seu cargo estas questões caem, leia o conteúdo programático no passo Cargo.
      </p>
    );
  } else if (!state) {
    body = (
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <p className="text-slate-600 dark:text-slate-400">Ligue as questões aos tópicos do seu cargo para ver o que mais cai.</p>
        {button("Ligar aos tópicos", true)}
      </div>
    );
  } else if (state.status === "PENDING") {
    body = (
      <p className="flex items-center gap-2 text-slate-600 dark:text-slate-400" role="status">
        <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Ligando as questões aos tópicos…
      </p>
    );
  } else if (state.status === "FAILED") {
    body = (
      <p className="flex flex-wrap items-center gap-x-3 text-slate-600 dark:text-slate-400">
        Não conseguimos ligar as questões aos tópicos{state.retryable ? " agora" : ""}.
        {state.retryable && button("Tentar de novo")}
      </p>
    );
  } else {
    body = (
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-slate-600 dark:text-slate-400">
        <span className="inline-flex items-center gap-1.5 text-emerald-700 dark:text-emerald-400">
          <Check className="size-4" aria-hidden="true" />
          Questões ligadas aos tópicos do seu cargo (veja no passo Cargo).
        </span>
        {state.outdated && button("Ligar de novo")}
      </p>
    );
  }

  return (
    <div className="mt-3 border-t border-slate-100 pt-3 dark:border-slate-800">
      {body}
      {classify.isError && (
        <p className="mt-2 text-red-700 dark:text-red-300">{errorMessage(classify.error, "Não foi possível pedir agora.")}</p>
      )}
    </div>
  );
}

const linkButton =
  "inline-flex items-center gap-1.5 text-sm font-semibold text-indigo-700 hover:underline disabled:opacity-60 dark:text-indigo-300";

const primarySmallButton =
  "inline-flex h-9 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";
