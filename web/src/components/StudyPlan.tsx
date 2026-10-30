import { BookOpen, CalendarCheck, Check, ChevronLeft, ChevronRight, ListChecks, RotateCcw, Timer } from "lucide-react";
import { type FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router";

import { ApiError, type PlanSession, type PlanSessionKind, type StudyPlan } from "../api";
import { usePractice, useSavePlan, useSessionDone } from "../data/plans";
import { addDays, formatMinutes, mondayOf, todayIso } from "../dates";
import { daysUntil, formatDate } from "../exams";
import { findTopic, shortTopic, topicPath } from "../topics";

const WEEKDAYS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

const MINUTE_OPTIONS = [0, 30, 60, 90, 120, 150, 180, 240, 300, 360, 480];

// o tipo da sessão: a faixa à esquerda (cor) e o ícone (revisão e simulado)
const KIND: Record<PlanSessionKind, { label: string; icon: typeof BookOpen; stripe: string; text: string }> = {
  STUDY: { label: "Estudo", icon: BookOpen, stripe: "border-l-indigo-500", text: "text-indigo-600 dark:text-indigo-400" },
  REVIEW: { label: "Revisão", icon: RotateCcw, stripe: "border-l-amber-500", text: "text-amber-600 dark:text-amber-400" },
  SIMULATION: { label: "Simulado", icon: Timer, stripe: "border-l-emerald-500", text: "text-emerald-600 dark:text-emerald-400" },
};

/**
 * Montar ou refazer o plano: a data da prova e as horas de cada dia da semana. Refazer recalcula
 * a partir de hoje e mantém o que já foi feito.
 */
export function PlanForm({
  targetId,
  plan,
  examDate,
  onDone,
}: {
  targetId: string;
  plan: StudyPlan | null;
  /** A data da prova do concurso, se já está nos dados. */
  examDate: string | null;
  onDone: () => void;
}) {
  const save = useSavePlan(targetId);
  const today = todayIso();
  const [endDate, setEndDate] = useState(plan?.endDate ?? (examDate && examDate > today ? examDate : ""));
  const [daily, setDaily] = useState<number[]>(plan?.dailyMinutes ?? [120, 120, 120, 120, 120, 180, 0]);
  const weekly = daily.reduce((total, minutes) => total + minutes, 0);

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate({ endDate, dailyMinutes: daily }, { onSuccess: onDone });
  }

  return (
    <form onSubmit={submit} className="mt-4 rounded-xl border border-indigo-200 bg-indigo-50/40 p-4 dark:border-indigo-500/30 dark:bg-indigo-500/5">
      <p className="font-semibold">{plan ? "Refazer o plano" : "Montar o plano de estudos"}</p>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        O tempo de cada disciplina sai do peso dela na prova, de quanto ela cai nas provas relacionadas e de quanto você
        erra nos simulados.{plan && " As sessões já feitas ficam; o resto é recalculado a partir de hoje."}
      </p>
      <label className="mt-4 grid max-w-xs gap-1.5 text-sm">
        <span className="text-slate-500 dark:text-slate-400">Data da prova</span>
        <input
          type="date"
          value={endDate}
          min={addDays(today, 1)}
          onChange={(e) => setEndDate(e.target.value)}
          required
          className="h-10 rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950"
        />
      </label>
      <fieldset className="mt-4">
        <legend className="text-sm text-slate-500 dark:text-slate-400">
          Tempo de estudo por dia <span className="font-medium text-slate-700 dark:text-slate-300">({formatMinutes(weekly)} por semana)</span>
        </legend>
        <div className="mt-1.5 grid grid-cols-4 gap-2 sm:grid-cols-7">
          {WEEKDAYS.map((label, index) => (
            <label key={label} className="grid gap-1 text-xs">
              <span className="font-medium text-slate-600 dark:text-slate-400">{label}</span>
              <select
                value={daily[index]}
                onChange={(e) => setDaily(daily.map((value, i) => (i === index ? Number(e.target.value) : value)))}
                className="h-9 rounded-lg border border-slate-200 bg-white px-1.5 text-sm dark:border-slate-800 dark:bg-slate-950"
              >
                {MINUTE_OPTIONS.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutes === 0 ? "folga" : formatMinutes(minutes)}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      </fieldset>
      {save.isError && (
        <p role="alert" className="mt-3 text-sm text-red-700 dark:text-red-300">
          {save.error instanceof ApiError && save.error.detail ? save.error.detail : "Não foi possível montar o plano agora."}
        </p>
      )}
      <div className="mt-4 flex gap-2">
        <button
          type="submit"
          disabled={!endDate || weekly === 0 || save.isPending}
          className="inline-flex h-10 items-center gap-2 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-indigo-500 dark:hover:bg-indigo-400"
        >
          {save.isPending ? "Montando…" : plan ? "Refazer" : "Montar plano"}
        </button>
        <button
          type="button"
          onClick={onDone}
          className="inline-flex h-10 items-center rounded-[10px] border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}

/**
 * Uma sessão: o "feito", a disciplina, os minutos e, havendo questões dos tópicos dela nas provas
 * relacionadas, o "Praticar". O tipo (estudo, revisão, simulado) aparece pela cor da faixa à esquerda.
 */
export function SessionItem({
  session,
  context,
  topicHref,
}: {
  session: PlanSession;
  context?: string;
  /** Página de um tópico da sessão (null se não achar no conteúdo): sem isso, os tópicos são só texto. */
  topicHref?: (subject: string | null, topic: string) => string | null;
}) {
  const done = useSessionDone();
  const practice = usePractice();
  const navigate = useNavigate();
  const [allTopics, setAllTopics] = useState(false);
  const kind = KIND[session.kind];
  const Icon = kind.icon;
  const title = session.kind === "SIMULATION" ? "Simulado completo" : (session.subject ?? "Revisão da semana");
  const shown = allTopics ? session.topics : session.topics.slice(0, 2);
  const hidden = session.topics.length - shown.length;

  return (
    <li
      className={`flex items-center gap-4 rounded-xl border border-l-4 border-slate-200 bg-white py-3.5 pr-4 pl-4 dark:border-slate-800 dark:bg-slate-950 ${kind.stripe} ${
        session.done ? "opacity-60" : ""
      }`}
    >
      <button
        type="button"
        onClick={() => done.mutate({ sessionId: session.id, done: !session.done })}
        aria-pressed={session.done}
        aria-label={session.done ? `Desmarcar ${title}` : `Marcar ${title} como feito`}
        className={`grid size-6 shrink-0 place-items-center rounded-full border-2 transition ${
          session.done
            ? "border-emerald-600 bg-emerald-600 text-white dark:border-emerald-500 dark:bg-emerald-500"
            : "border-slate-300 hover:border-indigo-500 dark:border-slate-600"
        }`}
      >
        {session.done && <Check className="size-3.5" aria-hidden="true" />}
      </button>
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 text-[15px] font-medium">
          {session.kind !== "STUDY" && <Icon className={`size-4 shrink-0 ${kind.text}`} aria-label={kind.label} />}
          <span className={`truncate ${session.done ? "line-through" : ""}`}>{title}</span>
        </p>
        {context ? (
          <p className="mt-1 truncate text-xs leading-relaxed text-slate-500 dark:text-slate-400">{context}</p>
        ) : (
          session.topics.length > 0 && (
            // cada tópico abre a página dele (resumo e questões)
            <p className={`mt-1 text-xs leading-relaxed text-slate-500 dark:text-slate-400 ${allTopics ? "" : "truncate"}`}>
              {shown.map((topic, index) => {
                const href = topicHref?.(session.subject, topic);
                return (
                  <span key={topic}>
                    {index > 0 && <span aria-hidden="true"> · </span>}
                    {href ? (
                      <Link to={href} title={topic} className="hover:text-indigo-700 hover:underline dark:hover:text-indigo-300">
                        {shortTopic(topic)}
                      </Link>
                    ) : (
                      <span title={topic}>{shortTopic(topic)}</span>
                    )}
                  </span>
                );
              })}
              {(hidden > 0 || allTopics) && session.topics.length > 2 && (
                <button
                  type="button"
                  onClick={() => setAllTopics(!allTopics)}
                  className="ml-1.5 font-semibold text-indigo-700 hover:underline dark:text-indigo-300"
                >
                  {allTopics ? "menos" : `+${hidden}`}
                </button>
              )}
            </p>
          )
        )}
        {practice.isError && (
          <p role="alert" className="text-xs text-red-700 dark:text-red-300">
            {practice.error instanceof ApiError && practice.error.detail ? practice.error.detail : "Não foi possível começar agora."}
          </p>
        )}
      </div>
      {session.practice ? (
        <button
          type="button"
          onClick={() =>
            practice.mutate(session.id, { onSuccess: (simulation) => void navigate(`/simulados/${simulation.id}`) })
          }
          disabled={practice.isPending}
          title={`Praticar ${session.practice} ${session.practice === 1 ? "questão" : "questões"} destes tópicos`}
          className="inline-flex shrink-0 items-center gap-1 rounded-full border border-slate-200 px-2.5 py-1 text-xs font-semibold text-indigo-700 transition hover:border-indigo-300 hover:bg-indigo-50 disabled:opacity-60 dark:border-slate-700 dark:text-indigo-300 dark:hover:bg-indigo-500/10"
        >
          <ListChecks className="size-3.5" aria-hidden="true" />
          {practice.isPending ? "…" : "Praticar"}
        </button>
      ) : null}
      <span className="w-12 shrink-0 text-right text-xs font-medium tabular-nums text-slate-500 dark:text-slate-400">
        {formatMinutes(session.minutes)}
      </span>
    </li>
  );
}

/** As sessões de cada dia. */
function byDay(sessions: PlanSession[]) {
  const days = new Map<string, PlanSession[]>();
  for (const session of sessions) {
    days.set(session.date, [...(days.get(session.date) ?? []), session]);
  }
  return days;
}

const longDay = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit", timeZone: "UTC" });

/** "terça-feira, 11/11" (com maiúscula). */
function formatLongDay(iso: string) {
  const [year, month, day] = iso.split("-").map(Number);
  const text = longDay.format(new Date(Date.UTC(year, month - 1, day)));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * O plano, pensado para o dia a dia: no topo, a prova e o progresso; depois a semana (um dia por
 * pílula, com as horas e o que já foi feito) e as sessões do dia escolhido, hoje por padrão. O tempo
 * por disciplina fica recolhido.
 */
export function PlanPanel({
  plan,
  subjects,
  onRedo,
}: {
  plan: StudyPlan;
  /** O conteúdo do cargo: os tópicos das sessões viram links para a página de cada um. */
  subjects: { name: string; topics: string[] }[];
  onRedo: () => void;
}) {
  const today = todayIso();
  const sessionsByDay = byDay(plan.sessions);
  const dates = [...sessionsByDay.keys()].sort();
  // hoje, se o plano já começou; senão o primeiro dia com sessão
  const initial = dates.length === 0 || today >= dates[0] ? today : dates[0];
  const [selected, setSelected] = useState(initial);
  const [weekStart, setWeekStart] = useState(mondayOf(initial));
  const firstWeek = mondayOf(dates[0] ?? today);
  const lastWeek = mondayOf(dates.at(-1) ?? today);

  const total = plan.sessions.reduce((sum, s) => sum + s.minutes, 0);
  const doneMinutes = plan.sessions.filter((s) => s.done).reduce((sum, s) => sum + s.minutes, 0);
  const percent = total ? Math.round((doneMinutes / total) * 100) : 0;
  const late = plan.sessions.filter((s) => !s.done && s.date < today);
  const daysLeft = daysUntil(plan.endDate);
  const maxSubject = Math.max(1, ...plan.subjects.map((s) => s.minutes));
  const week = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
  const selectedSessions = sessionsByDay.get(selected) ?? [];
  const isExamDay = selected === plan.endDate;

  function goWeek(delta: number) {
    const start = addDays(weekStart, delta * 7);
    setWeekStart(start);
    // na semana nova, o primeiro dia com sessão (ou a segunda)
    setSelected(Array.from({ length: 7 }, (_, i) => addDays(start, i)).find((d) => sessionsByDay.has(d)) ?? start);
  }

  return (
    <section className="mb-8 rounded-2xl border border-slate-200 bg-slate-50/60 p-6 dark:border-slate-800 dark:bg-slate-900/40">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="flex items-center gap-2 font-semibold">
            <CalendarCheck className="size-5 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
            Seu plano
          </h3>
          <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
            Prova em {formatDate(plan.endDate, true)}
            {daysLeft > 0 && ` · faltam ${daysLeft} ${daysLeft === 1 ? "dia" : "dias"}`}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <div className="text-right">
            <p className="text-lg font-bold tabular-nums">{percent}%</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">feito</p>
          </div>
          <button
            type="button"
            onClick={onRedo}
            title="Refazer o plano (mantém o que já foi feito)"
            className="grid size-9 place-items-center rounded-lg text-slate-500 transition hover:bg-white hover:text-indigo-700 dark:hover:bg-slate-800 dark:hover:text-indigo-300"
          >
            <RotateCcw className="size-4" aria-hidden="true" />
            <span className="sr-only">Refazer plano</span>
          </button>
        </div>
      </div>
      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
        <div className="h-full rounded-full bg-emerald-500" style={{ width: `${percent}%` }} />
      </div>

      {late.length > 0 && (
        <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">
          {late.length} {late.length === 1 ? "sessão atrasada" : "sessões atrasadas"}: o plano se ajusta sozinho esta
          madrugada, ou{" "}
          <button type="button" onClick={onRedo} className="font-semibold underline">
            refaça agora
          </button>
          .
        </p>
      )}

      {/* a semana: um dia por pílula */}
      <div className="mt-7 flex items-center gap-1">
        <button
          type="button"
          onClick={() => goWeek(-1)}
          disabled={weekStart <= firstWeek}
          aria-label="Semana anterior"
          className="grid size-8 shrink-0 place-items-center rounded-lg text-slate-500 transition hover:bg-white disabled:opacity-30 dark:hover:bg-slate-800"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
        </button>
        <ol className="grid flex-1 grid-cols-7 gap-1.5">
          {week.map((day) => {
            const items = sessionsByDay.get(day) ?? [];
            const minutes = items.reduce((sum, s) => sum + s.minutes, 0);
            const allDone = items.length > 0 && items.every((s) => s.done);
            const behind = day < today && items.some((s) => !s.done);
            const isSelected = day === selected;
            return (
              <li key={day}>
                <button
                  type="button"
                  onClick={() => setSelected(day)}
                  aria-pressed={isSelected}
                  aria-label={`${formatLongDay(day)}${minutes ? `, ${formatMinutes(minutes)}` : ", sem estudo"}`}
                  className={`flex w-full flex-col items-center rounded-xl py-2.5 transition ${
                    isSelected
                      ? "bg-indigo-600 text-white shadow-sm dark:bg-indigo-500"
                      : day === today
                        ? "bg-white ring-1 ring-indigo-300 dark:bg-slate-800 dark:ring-indigo-500/50"
                        : "hover:bg-white dark:hover:bg-slate-800"
                  }`}
                >
                  <span className={`text-[11px] uppercase ${isSelected ? "text-indigo-100" : "text-slate-500 dark:text-slate-400"}`}>
                    {WEEKDAYS[week.indexOf(day)]}
                  </span>
                  <span className="text-sm font-semibold tabular-nums">{Number(day.slice(8))}</span>
                  <span
                    className={`mt-0.5 flex h-4 items-center text-[10px] tabular-nums ${
                      isSelected ? "text-indigo-100" : "text-slate-500 dark:text-slate-400"
                    }`}
                  >
                    {allDone ? (
                      <Check className={`size-3.5 ${isSelected ? "" : "text-emerald-600 dark:text-emerald-400"}`} aria-hidden="true" />
                    ) : behind ? (
                      <span className="size-1.5 rounded-full bg-amber-500" aria-hidden="true" />
                    ) : minutes ? (
                      formatMinutes(minutes)
                    ) : day === plan.endDate ? (
                      "prova"
                    ) : (
                      "–"
                    )}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
        <button
          type="button"
          onClick={() => goWeek(1)}
          disabled={weekStart >= lastWeek}
          aria-label="Próxima semana"
          className="grid size-8 shrink-0 place-items-center rounded-lg text-slate-500 transition hover:bg-white disabled:opacity-30 dark:hover:bg-slate-800"
        >
          <ChevronRight className="size-4" aria-hidden="true" />
        </button>
      </div>

      {/* o dia escolhido */}
      <div className="mt-7">
        <p className="mb-3 flex items-baseline justify-between text-sm">
          <span className="font-semibold">{selected === today ? "Hoje" : formatLongDay(selected)}</span>
          {selectedSessions.length > 0 && (
            <span className="text-xs text-slate-500 dark:text-slate-400">
              {selectedSessions.filter((s) => s.done).length} de {selectedSessions.length} feitas ·{" "}
              {formatMinutes(selectedSessions.reduce((sum, s) => sum + s.minutes, 0))}
            </span>
          )}
        </p>
        {selectedSessions.length === 0 ? (
          <p className="rounded-lg border border-dashed border-slate-300 px-3 py-4 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            {isExamDay ? "Dia da prova. Boa sorte!" : "Sem estudo neste dia."}
          </p>
        ) : (
          <ul className="space-y-3">
            {selectedSessions.map((session) => (
              <SessionItem
                key={session.id}
                session={session}
                topicHref={(subject, topic) => {
                  const at = findTopic(subjects, subject, topic);
                  return at ? topicPath(plan.studyTargetId, at[0], at[1]) : null;
                }}
              />
            ))}
          </ul>
        )}
      </div>

      <details className="mt-7 group">
        <summary className="cursor-pointer list-none text-xs font-medium text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200 [&::-webkit-details-marker]:hidden">
          <ChevronRight className="mr-1 inline size-3.5 transition group-open:rotate-90" aria-hidden="true" />
          Tempo por disciplina
        </summary>
        <ul className="mt-2 space-y-2">
          {plan.subjects.map((subject) => (
            <li key={subject.subject} className="text-sm">
              <div className="flex justify-between gap-3">
                <span className="min-w-0 truncate">{subject.subject}</span>
                <span className="shrink-0 text-xs tabular-nums text-slate-500 dark:text-slate-400">
                  {formatMinutes(subject.doneMinutes)} de {formatMinutes(subject.minutes)}
                </span>
              </div>
              {/* barra: o tamanho da disciplina no plano; o verde, o que já foi feito */}
              <div className="mt-1 h-1.5 rounded-full bg-slate-200 dark:bg-slate-800" style={{ width: `${Math.max(8, Math.round((subject.minutes / maxSubject) * 100))}%` }}>
                <div
                  className="h-full rounded-full bg-emerald-500"
                  style={{ width: `${subject.minutes ? Math.round((subject.doneMinutes / subject.minutes) * 100) : 0}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
