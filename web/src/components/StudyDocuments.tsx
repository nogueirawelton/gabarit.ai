import { FileText, Plus, Trash2, Upload } from "lucide-react";
import { type ChangeEvent, type FormEvent, useRef, useState } from "react";

import { ApiError, type StudyDocument, type StudyDocumentKind, type StudyPaper } from "../api";
import {
  MAX_DOCUMENT_BYTES,
  useAddPaper,
  useRemoveDocument,
  useRemovePaper,
  useStudyMaterials,
  useUploadDocument,
} from "../data/study";
import { EditalReading } from "./EditalReading";

const sizeFormat = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

function formatSize(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${sizeFormat.format(bytes / 1024 / 1024)} MB`;
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.detail ? error.detail : fallback;
}

/**
 * Materiais do concurso: editais e provas relacionadas. Cada prova relacionada tem o espaço da prova e o
 * do gabarito dela, para o par ficar explícito. O PDF vai do navegador direto para o armazenamento e
 * o servidor confere (é PDF? abre? tamanho e páginas no limite); recusado mostra o motivo.
 */
export function StudyDocuments({ targetId }: { targetId: string }) {
  const materials = useStudyMaterials(targetId);

  return (
    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="text-lg font-semibold">Edital e provas relacionadas</h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        PDF de até 30 MB. A partir desses arquivos vamos montar o conteúdo do seu cargo, os simulados e o plano de
        estudos.
      </p>

      {materials.isError ? (
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">Não foi possível carregar os arquivos agora.</p>
      ) : materials.isPending ? (
        <p className="mt-6 text-sm text-slate-500 dark:text-slate-400" role="status">
          Carregando…
        </p>
      ) : (
        <>
          <Editais targetId={targetId} editais={materials.data.editais} />
          <Papers targetId={targetId} papers={materials.data.papers} />
        </>
      )}
    </section>
  );
}

function Editais({ targetId, editais }: { targetId: string; editais: StudyDocument[] }) {
  return (
    <div className="mt-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-medium">Edital do concurso</h3>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
            O edital do concurso que você vai prestar e as retificações, se houver.
          </p>
        </div>
        <UploadButton targetId={targetId} kind="EDITAL" label="Enviar edital" />
      </div>
      {editais.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-100 rounded-xl border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
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

function Papers({ targetId, papers }: { targetId: string; papers: StudyPaper[] }) {
  const [adding, setAdding] = useState(false);

  return (
    <div className="mt-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-medium">Provas relacionadas</h3>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
            Provas de outros concursos do mesmo cargo ou banca (ou de edições passadas deste), cada uma com o
            seu gabarito e, se tiver, o edital dela.
          </p>
        </div>
        {!adding && (
          <button type="button" onClick={() => setAdding(true)} className={smallButton}>
            <Plus className="size-4" aria-hidden="true" />
            Adicionar prova
          </button>
        )}
      </div>

      {adding && <AddPaperForm targetId={targetId} onDone={() => setAdding(false)} />}

      {papers.length > 0 && (
        <ul className="mt-3 grid gap-3">
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

const primarySmallButton =
  "inline-flex h-9 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";
