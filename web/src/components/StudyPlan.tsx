import { BookOpen, CalendarCheck, Check, ListChecks, RotateCcw, Timer } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router";

import { ApiError, type PlanSession, type PlanSessionKind, type StudyPlan } from "../api";
import { usePractice, useSavePlan, useSessionDone } from "../data/plans";
import { addDays, formatMinutes, formatWeekday, mondayOf, todayIso } from "../dates";
import { daysUntil, formatDate } from "../exams";

const WEEKDAYS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

const MINUTE_OPTIONS = [0, 30, 60, 90, 120, 150, 180, 240, 300, 360, 480];

const KIND: Record<PlanSessionKind, { label: string; icon: typeof BookOpen }> = {
  STUDY: { label: "Estudo", icon: BookOpen },
  REVIEW: { label: "Revisão", icon: RotateCcw },
  SIMULATION: { label: "Simulado", icon: Timer },
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
 * Uma sessão com o "feito" (no plano e na agenda) e, havendo questões dos tópicos dela nas provas
 * relacionadas, o "Praticar".
 */
export function SessionItem({ session, context }: { session: PlanSession; context?: string }) {
  const done = useSessionDone();
  const practice = usePractice();
  const navigate = useNavigate();
  const { label, icon: Icon } = KIND[session.kind];
  const title = session.kind === "SIMULATION" ? "Simulado completo" : (session.subject ?? "Revisão da semana");
  return (
    <li className="flex items-start gap-3 py-2">
      <button
        type="button"
        onClick={() => done.mutate({ sessionId: session.id, done: !session.done })}
        aria-pressed={session.done}
        aria-label={session.done ? `Desmarcar ${title}` : `Marcar ${title} como feito`}
        className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-md border transition ${
          session.done
            ? "border-emerald-600 bg-emerald-600 text-white dark:border-emerald-500 dark:bg-emerald-500"
            : "border-slate-300 hover:border-indigo-500 dark:border-slate-600"
        }`}
      >
        {session.done && <Check className="size-4" aria-hidden="true" />}
      </button>
      <div className={`min-w-0 flex-1 ${session.done ? "opacity-60" : ""}`}>
        <p className="flex flex-wrap items-center gap-x-2 text-sm font-medium">
          <Icon className="size-4 shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
          <span className={session.done ? "line-through" : ""}>{title}</span>
          <span className="font-normal text-slate-500 dark:text-slate-400">
            · {label} · {formatMinutes(session.minutes)}
          </span>
        </p>
        {context && <p className="text-xs text-slate-500 dark:text-slate-400">{context}</p>}
        {session.topics.length > 0 && (
          <p className="mt-0.5 line-clamp-2 text-xs text-slate-600 dark:text-slate-400">{session.topics.join(" · ")}</p>
        )}
        {practice.isError && (
          <p role="alert" className="mt-1 text-xs text-red-700 dark:text-red-300">
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
          className="inline-flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-indigo-700 transition hover:bg-indigo-50 disabled:opacity-60 dark:text-indigo-300 dark:hover:bg-indigo-500/10"
        >
          <ListChecks className="size-3.5" aria-hidden="true" />
          {practice.isPending ? "Abrindo…" : `Praticar (${session.practice})`}
        </button>
      ) : null}
    </li>
  );
}

/** Dias (em ordem) com as sessões de cada um. */
function byDay(sessions: PlanSession[]) {
  const days = new Map<string, PlanSession[]>();
  for (const session of sessions) {
    days.set(session.date, [...(days.get(session.date) ?? []), session]);
  }
  return [...days.entries()];
}

/**
 * O plano: progresso, tempo por disciplina, a semana atual (com o "feito") e, abrindo, as próximas.
 * Sessões passadas não feitas aparecem como atrasadas, com a sugestão de refazer.
 */
export function PlanPanel({ plan, onRedo }: { plan: StudyPlan; onRedo: () => void }) {
  const [showAll, setShowAll] = useState(false);
  const today = todayIso();
  const weekEnd = addDays(mondayOf(today), 6);
  const total = plan.sessions.reduce((sum, s) => sum + s.minutes, 0);
  const doneMinutes = plan.sessions.filter((s) => s.done).reduce((sum, s) => sum + s.minutes, 0);
  const late = plan.sessions.filter((s) => !s.done && s.date < today);
  const thisWeek = plan.sessions.filter((s) => s.date >= today && s.date <= weekEnd);
  const later = plan.sessions.filter((s) => s.date > weekEnd);
  const days = daysUntil(plan.endDate);
  const maxSubject = Math.max(1, ...plan.subjects.map((s) => s.minutes));

  return (
    <section className="mb-6 rounded-xl border border-slate-200 p-5 dark:border-slate-800">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 font-semibold">
            <CalendarCheck className="size-5 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
            Seu plano até a prova
          </h3>
          <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
            Prova em {formatDate(plan.endDate, true)}
            {days > 0 && ` · faltam ${days} ${days === 1 ? "dia" : "dias"}`} · {formatMinutes(doneMinutes)} de{" "}
            {formatMinutes(total)} feitos
          </p>
        </div>
        <button type="button" onClick={onRedo} className="text-sm font-semibold text-indigo-700 hover:underline dark:text-indigo-300">
          Refazer plano
        </button>
      </div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <div
          className="h-full rounded-full bg-emerald-600 dark:bg-emerald-500"
          style={{ width: `${total ? Math.round((doneMinutes / total) * 100) : 0}%` }}
        />
      </div>

      {late.length > 0 && (
        <p className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-400/10 dark:text-amber-300">
          {late.length} {late.length === 1 ? "sessão ficou" : "sessões ficaram"} para trás.{" "}
          <button type="button" onClick={onRedo} className="font-semibold underline">
            Refaça o plano
          </button>{" "}
          para redistribuir o tempo até a prova.
        </p>
      )}

      <details className="mt-4">
        <summary className="cursor-pointer text-sm font-medium text-slate-700 dark:text-slate-300">
          Tempo por disciplina
        </summary>
        <ul className="mt-2 space-y-2">
          {plan.subjects.map((subject) => (
            <li key={subject.subject} className="text-sm">
              <div className="flex justify-between gap-3">
                <span className="min-w-0 truncate">{subject.subject}</span>
                <span className="shrink-0 tabular-nums text-slate-500 dark:text-slate-400">
                  {formatMinutes(subject.doneMinutes)} / {formatMinutes(subject.minutes)}
                </span>
              </div>
              <div className="mt-1 h-1.5 rounded-full bg-slate-100 dark:bg-slate-800">
                <div
                  className="h-full rounded-full bg-indigo-600 dark:bg-indigo-500"
                  style={{ width: `${Math.round((subject.minutes / maxSubject) * 100)}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      </details>

      <h4 className="mt-5 text-sm font-semibold">Esta semana</h4>
      {thisWeek.length === 0 ? (
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Nada marcado para o resto da semana.</p>
      ) : (
        <Days sessions={thisWeek} today={today} />
      )}

      {later.length > 0 && (
        <>
          <button
            type="button"
            onClick={() => setShowAll(!showAll)}
            className="mt-4 text-sm font-semibold text-indigo-700 hover:underline dark:text-indigo-300"
          >
            {showAll ? "Esconder as próximas semanas" : `Ver as próximas semanas (${later.length} sessões)`}
          </button>
          {showAll && <Days sessions={later} today={today} />}
        </>
      )}
    </section>
  );
}

function Days({ sessions, today }: { sessions: PlanSession[]; today: string }) {
  return (
    <div className="mt-2 space-y-3">
      {byDay(sessions).map(([day, items]) => (
        <div key={day}>
          <p className="text-xs font-semibold tracking-wide text-slate-500 uppercase dark:text-slate-400">
            {day === today ? "Hoje" : formatWeekday(day)} · {formatMinutes(items.reduce((sum, s) => sum + s.minutes, 0))}
          </p>
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {items.map((session) => (
              <SessionItem key={session.id} session={session} />
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
