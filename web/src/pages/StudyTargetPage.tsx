import { ArrowLeft, Check, ExternalLink, Trash2 } from "lucide-react";
import { type FormEvent, type ReactNode, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";

import { ApiError, isUuid, type StudyTargetDetail, type StudyTargetUpdate } from "../api";
import { displayPositionName } from "../brazil";
import { StatusBadge } from "../components/ExamCard";
import { ExamDetailSkeleton } from "../components/Skeleton";
import { StudyDocuments } from "../components/StudyDocuments";
import { useRemoveStudyTarget, useStudyTarget, useUpdateStudyTarget } from "../data/study";
import { primaryButton, secondaryButton } from "./StudiesPage";

/** Um concurso da carteira: dados (cargo, banca, data da prova) e os arquivos (edital, provas, gabaritos). */
export function StudyTargetPage() {
  const id = useParams().id ?? "";
  const target = useStudyTarget(id);

  if (!isUuid(id) || (target.error instanceof ApiError && target.error.status === 404)) {
    return (
      <>
        <BackLink />
        <h1 className="mt-6 text-2xl font-bold tracking-tight">Concurso não encontrado na sua carteira</h1>
      </>
    );
  }
  if (target.isPending) {
    return <ExamDetailSkeleton />;
  }
  if (target.isError) {
    return (
      <>
        <BackLink />
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">Não foi possível carregar agora. Recarregue a página.</p>
      </>
    );
  }

  const data = target.data;
  return (
    <>
      <BackLink />
      <div className="mt-6 flex flex-wrap items-center gap-2">
        {data.exam ? (
          <>
            <StatusBadge exam={data.exam} />
            <Link
              to={`/concursos/${data.exam.id}`}
              className="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400"
            >
              Ver concurso
              <ExternalLink className="size-3.5" aria-hidden="true" />
            </Link>
          </>
        ) : (
          <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            Cadastrado por você
          </span>
        )}
      </div>
      <h1 className="mt-3 text-2xl font-bold tracking-tight text-balance sm:text-3xl">{data.title}</h1>

      {/* key: ao trocar de concurso (ou depois de salvar ou aplicar o edital), o formulário recomeça do que está salvo */}
      <DetailsForm
        key={[data.id, data.organ, data.board, data.year, data.positionName, data.examDate].join("|")}
        target={data}
      />

      <StudyDocuments targetId={data.id} />

      <RemoveButton id={data.id} />
    </>
  );
}

function DetailsForm({ target }: { target: StudyTargetDetail }) {
  const update = useUpdateStudyTarget(target.id);
  const [form, setForm] = useState({
    title: target.title,
    organ: target.organ ?? "",
    board: target.board ?? "",
    year: target.year?.toString() ?? "",
    positionName: target.positionName ?? "",
    examDate: target.examDate ?? "",
  });
  const [saved, setSaved] = useState(false);
  const positions = target.exam?.positions ?? [];
  const suggested = target.suggestedPositions;

  function set(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setSaved(false);
    update.reset();
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const body: StudyTargetUpdate = {
      title: form.title.trim(),
      organ: form.organ || null,
      board: form.board || null,
      year: form.year ? Number(form.year) : null,
      positionName: form.positionName || null,
      examDate: form.examDate || null,
    };
    update.mutate(body, { onSuccess: () => setSaved(true) });
  }

  return (
    <form
      onSubmit={submit}
      className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900"
    >
      <h2 className="text-lg font-semibold">Dados do concurso</h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        O cargo define o conteúdo que você vai estudar. A data da prova é a base do plano de estudos.
      </p>

      <div className="mt-6 grid gap-4 sm:grid-cols-2">
        <Field label="Título" className="sm:col-span-2">
          <input value={form.title} onChange={(e) => set("title", e.target.value)} required maxLength={200} className={inputClass} />
        </Field>
        <Field label="Cargo" className="sm:col-span-2">
          {positions.length > 0 ? (
            <select value={form.positionName} onChange={(e) => set("positionName", e.target.value)} className={inputClass}>
              <option value="">Escolha o seu cargo</option>
              {/* os cargos do perfil primeiro: é por eles que o concurso chegou até a pessoa */}
              {suggested.length > 0 ? (
                <>
                  <optgroup label="Seus cargos">
                    {suggested.map((name) => (
                      <option key={name} value={name}>
                        {displayPositionName(name)}
                      </option>
                    ))}
                  </optgroup>
                  <optgroup label="Outros cargos do concurso">
                    {positions
                      .filter((position) => !suggested.includes(position.name))
                      .map((position) => (
                        <option key={position.id} value={position.name}>
                          {displayPositionName(position.name)}
                        </option>
                      ))}
                  </optgroup>
                </>
              ) : (
                positions.map((position) => (
                  <option key={position.id} value={position.name}>
                    {displayPositionName(position.name)}
                  </option>
                ))
              )}
            </select>
          ) : (
            <input
              value={form.positionName}
              onChange={(e) => set("positionName", e.target.value)}
              maxLength={200}
              placeholder="Ex.: Escrevente Técnico Judiciário"
              className={inputClass}
            />
          )}
        </Field>
        <Field label="Órgão">
          <input value={form.organ} onChange={(e) => set("organ", e.target.value)} maxLength={200} className={inputClass} />
        </Field>
        <Field label="Banca">
          <input
            value={form.board}
            onChange={(e) => set("board", e.target.value)}
            maxLength={100}
            placeholder="Ex.: FGV, Cebraspe, Vunesp"
            className={inputClass}
          />
        </Field>
        <Field label="Ano">
          <input
            value={form.year}
            onChange={(e) => set("year", e.target.value.replace(/\D/g, "").slice(0, 4))}
            inputMode="numeric"
            placeholder="Ex.: 2023"
            className={inputClass}
          />
        </Field>
        <Field label="Data da prova">
          <input type="date" value={form.examDate} onChange={(e) => set("examDate", e.target.value)} className={inputClass} />
        </Field>
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-3">
        <button type="submit" disabled={!form.title.trim() || update.isPending} className={primaryButton}>
          {update.isPending ? "Salvando…" : "Salvar"}
        </button>
        <p role="status" className="text-sm">
          {saved && (
            <span className="inline-flex items-center gap-1 font-medium text-emerald-700 dark:text-emerald-400">
              <Check className="size-4" aria-hidden="true" />
              Salvo
            </span>
          )}
          {update.isError && (
            <span className="text-red-700 dark:text-red-300">
              {update.error instanceof ApiError && update.error.detail
                ? update.error.detail
                : "Não foi possível salvar agora."}
            </span>
          )}
        </p>
      </div>
    </form>
  );
}

function RemoveButton({ id }: { id: string }) {
  const remove = useRemoveStudyTarget(id);
  const navigate = useNavigate();

  function confirmRemove() {
    if (window.confirm("Remover este concurso dos seus estudos?")) {
      remove.mutate(undefined, { onSuccess: () => void navigate("/estudos") });
    }
  }

  return (
    <div className="mt-8">
      <button
        type="button"
        onClick={confirmRemove}
        disabled={remove.isPending}
        className={`${secondaryButton} text-red-700 hover:border-red-300 dark:text-red-300 dark:hover:border-red-500/50`}
      >
        <Trash2 className="size-4" aria-hidden="true" />
        {remove.isPending ? "Removendo…" : "Remover dos estudos"}
      </button>
      {remove.isError && <p className="mt-2 text-sm text-red-700 dark:text-red-300">Não foi possível remover agora.</p>}
    </div>
  );
}

function Field({ label, children, className = "" }: { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={`grid gap-1.5 text-sm ${className}`}>
      <span className="text-slate-500 dark:text-slate-400">{label}</span>
      {children}
    </label>
  );
}

function BackLink() {
  return (
    <Link
      to="/estudos"
      className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Estudos
    </Link>
  );
}

const inputClass =
  "h-10 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950";
