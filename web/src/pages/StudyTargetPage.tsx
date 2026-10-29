import {
  ArrowLeft,
  ArrowRight,
  CalendarCheck,
  Check,
  ExternalLink,
  ListChecks,
  type LucideIcon,
  Timer,
  Trash2,
} from "lucide-react";
import { type FormEvent, type ReactNode, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";

import { ApiError, isUuid, type StudyDocument, type StudyTargetDetail, type StudyTargetUpdate } from "../api";
import { displayPositionName } from "../brazil";
import { EditalPositions } from "../components/EditalReading";
import { EditalSyllabus } from "../components/EditalSyllabus";
import { StatusBadge } from "../components/ExamCard";
import { ExamDetailSkeleton } from "../components/Skeleton";
import { EditalFiles, RelatedPapers } from "../components/StudyDocuments";
import { useRemoveStudyTarget, useStudyMaterials, useStudyTarget, useUpdateStudyTarget } from "../data/study";
import { primaryButton, secondaryButton } from "./StudiesPage";

type StepId = "concurso" | "edital" | "cargo" | "provas" | "estudar";

const STEPS: { id: StepId; label: string; title: string; description: string; optional?: boolean }[] = [
  {
    id: "concurso",
    label: "Concurso",
    title: "Dados do concurso",
    description: "Confira o título, o órgão e a banca. A data da prova é a base do plano de estudos.",
  },
  {
    id: "edital",
    label: "Edital",
    title: "Edital",
    description:
      "Envie o PDF do edital (e as retificações, se houver) e peça a leitura: a IA traz órgão, banca, datas e a lista de cargos.",
  },
  {
    id: "cargo",
    label: "Cargo",
    title: "Seu cargo",
    description: "Escolha o seu cargo entre os do edital. A partir dele lemos o conteúdo programático que você vai estudar.",
  },
  {
    id: "provas",
    label: "Provas",
    title: "Provas relacionadas",
    description:
      "Opcional. Provas de outros concursos do mesmo cargo ou banca (ou de edições passadas deste), cada uma com o seu gabarito. Elas viram simulados e mostram o estilo da banca.",
    optional: true,
  },
  {
    id: "estudar",
    label: "Estudar",
    title: "Tudo pronto para estudar",
    description: "A partir do conteúdo do seu cargo e das provas relacionadas.",
  },
];

/**
 * Um concurso da carteira, passo a passo: dados → edital (enviar e ler) → cargo (e o conteúdo
 * programático dele) → provas relacionadas (opcional) → estudar. O passo aberto fica na URL
 * (`?passo=`); sem ele, abre o primeiro que falta. Passo que depende de outro fica travado.
 */
export function StudyTargetPage() {
  const id = useParams().id ?? "";
  const target = useStudyTarget(id);
  const materials = useStudyMaterials(id);
  const [params, setParams] = useSearchParams();

  if (!isUuid(id) || (target.error instanceof ApiError && target.error.status === 404)) {
    return (
      <>
        <BackLink />
        <h1 className="mt-6 text-2xl font-bold tracking-tight">Concurso não encontrado nos seus concursos</h1>
      </>
    );
  }
  if (target.isPending || materials.isPending) {
    return <ExamDetailSkeleton />;
  }
  if (target.isError || materials.isError) {
    return (
      <>
        <BackLink />
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">Não foi possível carregar agora. Recarregue a página.</p>
      </>
    );
  }

  const data = target.data;
  const { editais, papers } = materials.data;
  // o edital do cargo: o que tem a escolha, ou o primeiro já lido (as retificações vêm depois)
  const readEdital = editais.find((edital) => edital.reading?.status === "DONE" && edital.reading.overview);
  const mainEdital = editais.find((edital) => edital.position) ?? readEdital;
  const done: Record<StepId, boolean> = {
    concurso: true,
    edital: Boolean(readEdital),
    cargo: Boolean(mainEdital?.position),
    provas: papers.some((paper) => paper.exam?.status === "READY"),
    estudar: false,
  };
  const unlocked: Record<StepId, boolean> = {
    concurso: true,
    edital: true,
    cargo: done.edital,
    provas: done.edital && done.cargo,
    estudar: done.edital && done.cargo,
  };
  const firstMissing = (["edital", "cargo"] as const).find((step) => !done[step]) ?? "estudar";
  const asked = STEPS.find((step) => step.id === params.get("passo"))?.id;
  const current = asked && unlocked[asked] ? asked : firstMissing;
  const index = STEPS.findIndex((step) => step.id === current);
  const step = STEPS[index];
  const next = STEPS[index + 1];

  function go(stepId: StepId) {
    setParams({ passo: stepId });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

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
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <h1 className="min-w-0 flex-1 text-2xl font-bold tracking-tight text-balance sm:text-3xl">{data.title}</h1>
        <RemoveButton id={data.id} />
      </div>

      <Stepper current={current} done={done} unlocked={unlocked} onSelect={go} />

      <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
        <p className="text-xs font-semibold tracking-wide text-indigo-700 uppercase dark:text-indigo-300">
          Passo {index + 1} de {STEPS.length}
          {step.optional && " · opcional"}
        </p>
        <h2 className="mt-1 text-lg font-semibold">{step.title}</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{step.description}</p>

        <div className="mt-6">
          {current === "concurso" && (
            // key: ao trocar de concurso (ou depois de salvar ou aplicar o edital), o formulário recomeça do que está salvo
            <DetailsForm
              key={[data.id, data.organ, data.board, data.year, data.positionName, data.examDate].join("|")}
              target={data}
            />
          )}
          {current === "edital" && <EditalFiles targetId={data.id} editais={editais} />}
          {current === "cargo" && mainEdital?.reading?.overview && (
            <>
              <EditalPositions targetId={data.id} document={mainEdital} overview={mainEdital.reading.overview} />
              <EditalSyllabus targetId={data.id} document={mainEdital} />
            </>
          )}
          {current === "provas" && <RelatedPapers targetId={data.id} papers={papers} />}
          {current === "estudar" && <StudyActions edital={mainEdital} papers={papers.length} onGo={go} />}
        </div>

        <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5 dark:border-slate-800">
          {index > 0 ? (
            <button type="button" onClick={() => go(STEPS[index - 1].id)} className={secondaryButton}>
              <ArrowLeft className="size-4" aria-hidden="true" />
              Voltar
            </button>
          ) : (
            <span />
          )}
          {next && (
            <div className="flex flex-wrap items-center justify-end gap-3">
              {!unlocked[next.id] && <span className="text-sm text-slate-500 dark:text-slate-400">{lockedHint(next.id)}</span>}
              <button type="button" onClick={() => go(next.id)} disabled={!unlocked[next.id]} className={primaryButton}>
                {step.optional && !done[current] ? "Pular" : "Continuar"}
                <ArrowRight className="size-4" aria-hidden="true" />
              </button>
            </div>
          )}
        </div>
      </section>
    </>
  );
}

function lockedHint(step: StepId) {
  return step === "cargo" ? "Envie e leia o edital para continuar." : "Escolha o seu cargo para continuar.";
}

/** Barra de passos: feito (✓), atual, disponível ou travado. No celular, rola de lado. */
function Stepper({
  current,
  done,
  unlocked,
  onSelect,
}: {
  current: StepId;
  done: Record<StepId, boolean>;
  unlocked: Record<StepId, boolean>;
  onSelect: (step: StepId) => void;
}) {
  return (
    <nav aria-label="Passos" className="scroll-thin mt-6 -mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <ol className="flex min-w-max items-center gap-2 sm:min-w-0">
        {STEPS.map((step, index) => {
          const isCurrent = step.id === current;
          const isDone = done[step.id] && !isCurrent;
          return (
            <li key={step.id} className="flex items-center gap-2 sm:flex-1">
              <button
                type="button"
                onClick={() => onSelect(step.id)}
                disabled={!unlocked[step.id]}
                aria-current={isCurrent ? "step" : undefined}
                className={`flex items-center gap-2 rounded-full py-1 pr-3 pl-1 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${
                  isCurrent
                    ? "bg-indigo-50 text-indigo-800 dark:bg-indigo-500/15 dark:text-indigo-200"
                    : "text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                }`}
              >
                <span
                  className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold ${
                    isCurrent
                      ? "bg-indigo-600 text-white dark:bg-indigo-500"
                      : isDone
                        ? "bg-emerald-600 text-white dark:bg-emerald-500"
                        : "border border-slate-300 text-slate-500 dark:border-slate-700"
                  }`}
                >
                  {isDone ? <Check className="size-4" aria-hidden="true" /> : index + 1}
                </span>
                {step.label}
              </button>
              {index < STEPS.length - 1 && (
                <span className="h-px w-6 shrink-0 bg-slate-200 sm:w-auto sm:flex-1 dark:bg-slate-800" aria-hidden="true" />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/**
 * O que dá para fazer com o concurso montado. Ainda não existe: os botões aparecem com "em breve" e
 * dizem do que cada um depende (o conteúdo do cargo; as provas relacionadas para o simulado).
 */
function StudyActions({
  edital,
  papers,
  onGo,
}: {
  edital: StudyDocument | undefined;
  papers: number;
  onGo: (step: StepId) => void;
}) {
  const syllabus = edital?.syllabus;
  const syllabusReady = syllabus?.status === "DONE" && syllabus.syllabus?.positionFound;
  const actions: { icon: LucideIcon; title: string; description: string; needs?: ReactNode }[] = [
    {
      icon: CalendarCheck,
      title: "Plano de estudos",
      description: "Agenda até a data da prova, com as disciplinas do seu cargo na ordem que mais rende.",
      needs: !syllabusReady && "Precisa do conteúdo programático do cargo.",
    },
    {
      icon: Timer,
      title: "Simulado",
      description: "Uma prova relacionada inteira, com tempo de prova e correção pelo gabarito.",
      needs:
        papers === 0 ? (
          <>
            Precisa de uma prova relacionada.{" "}
            <button type="button" onClick={() => onGo("provas")} className="font-semibold text-indigo-700 hover:underline dark:text-indigo-300">
              Adicionar
            </button>
          </>
        ) : undefined,
    },
    {
      icon: ListChecks,
      title: "Questões no estilo da banca",
      description: "Questões para resolver por disciplina e tópico, no formato da prova do seu cargo.",
      needs: !syllabusReady && "Precisa do conteúdo programático do cargo.",
    },
  ];

  return (
    <div>
      {!syllabusReady && (
        <p className="mb-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-400/10 dark:text-amber-300">
          {syllabus?.status === "PENDING" ? "O conteúdo programático do seu cargo ainda está sendo lido. " : "O conteúdo programático do seu cargo não está pronto. "}
          <button type="button" onClick={() => onGo("cargo")} className="font-semibold underline">
            Ver no passo Cargo
          </button>
        </p>
      )}
      <ul className="grid gap-3 sm:grid-cols-3">
        {actions.map(({ icon: Icon, title, description, needs }) => (
          <li
            key={title}
            className="flex flex-col rounded-xl border border-slate-200 p-4 dark:border-slate-800"
          >
            <span className="grid size-10 place-items-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <p className="mt-3 font-semibold">{title}</p>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{description}</p>
            {needs && <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{needs}</p>}
            {/* mt-auto: botões alinhados no fim dos cartões; pt-4: nunca colado no texto */}
            <div className="mt-auto pt-4">
              <button type="button" disabled className={`${primaryButton} w-full justify-center`}>
                Em breve
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
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
    <form onSubmit={submit}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Título" className="sm:col-span-2">
          <input value={form.title} onChange={(e) => set("title", e.target.value)} required maxLength={200} className={inputClass} />
        </Field>
        {/* concurso da lista: o cargo como a lista do concurso escreve (é o que aparece nos seus
            concursos). O cargo do edital, base do conteúdo, é escolhido no passo Cargo; no concurso
            cadastrado à mão é só esse */}
        {positions.length > 0 && (
          <Field label="Cargo (como aparece na lista do concurso)" className="sm:col-span-2">
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
          </Field>
        )}
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
    if (window.confirm("Remover este concurso de Meus concursos? Os arquivos e as leituras dele saem junto.")) {
      remove.mutate(undefined, { onSuccess: () => void navigate("/meus-concursos") });
    }
  }

  // ao lado do título: à vista, mas a confirmação diz o que sai junto
  return (
    <div className="flex shrink-0 flex-col items-end">
      <button
        type="button"
        onClick={confirmRemove}
        disabled={remove.isPending}
        className={`${secondaryButton} text-red-700 hover:border-red-300 dark:text-red-300 dark:hover:border-red-500/50`}
      >
        <Trash2 className="size-4" aria-hidden="true" />
        {remove.isPending ? "Removendo…" : "Remover"}
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
      to="/meus-concursos"
      className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Meus concursos
    </Link>
  );
}

const inputClass =
  "h-10 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950";
