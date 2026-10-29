import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";

import type { StudyTargetSummary } from "../api";
import { useAgendaSessions } from "../data/plans";
import { addDays, isoDate, todayIso } from "../dates";
import { daysUntil, formatDate } from "../exams";
import { SessionItem } from "./StudyPlan";

// STUDY: dia com sessão do plano de estudos (só a marca no mês)
type EventKind = "REGISTRATION_START" | "REGISTRATION_END" | "EXAM" | "STUDY";

type AgendaEvent = { date: string; kind: EventKind; target: StudyTargetSummary };

const KIND: Record<EventKind, { label: string; dot: string }> = {
  REGISTRATION_START: { label: "Inscrições abrem", dot: "bg-sky-500" },
  REGISTRATION_END: { label: "Inscrições terminam", dot: "bg-amber-500" },
  EXAM: { label: "Prova", dot: "bg-indigo-600 dark:bg-indigo-400" },
  STUDY: { label: "Estudo", dot: "bg-emerald-500" },
};

const WEEKDAYS = ["D", "S", "T", "Q", "Q", "S", "S"];

const monthFormat = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric", timeZone: "UTC" });

/** Inscrições (só de concurso da lista: as datas vêm dela) e prova de cada concurso, de hoje em diante. */
function upcomingEvents(targets: StudyTargetSummary[], today: string): AgendaEvent[] {
  const events: AgendaEvent[] = [];
  for (const target of targets) {
    const add = (date: string | null | undefined, kind: EventKind) => {
      if (date && date >= today) {
        events.push({ date, kind, target });
      }
    };
    add(target.exam?.registrationStart, "REGISTRATION_START");
    add(target.exam?.registrationEnd, "REGISTRATION_END");
    add(target.examDate, "EXAM");
  }
  // mesmo dia: prova primeiro (é o que importa)
  const order: EventKind[] = ["EXAM", "REGISTRATION_END", "REGISTRATION_START"];
  return events.sort((a, b) => a.date.localeCompare(b.date) || order.indexOf(a.kind) - order.indexOf(b.kind));
}

function countdown(date: string) {
  const days = daysUntil(date);
  return days === 0 ? "hoje" : days === 1 ? "amanhã" : `em ${days} dias`;
}

/**
 * Agenda dos meus concursos (embaixo da lista): um mês pequeno com marcas nos dias com evento e a lista das próximas
 * datas (inscrições e prova). Concurso sem data da prova aparece no fim, com atalho para completar.
 */
export function StudyAgenda({ targets }: { targets: StudyTargetSummary[] }) {
  const today = todayIso();
  const events = upcomingEvents(targets, today);
  const withoutDate = targets.filter((target) => !target.examDate);
  // sessões dos planos de estudos: os próximos dois meses (o mês pequeno navega até eles)
  const sessions = useAgendaSessions(today, addDays(today, 61));
  const studyDays = [...new Set((sessions.data ?? []).map((session) => session.date))].map((date) => ({
    date,
    kind: "STUDY" as const,
  }));
  const todaySessions = (sessions.data ?? []).filter((session) => session.date === today);

  return (
    <aside className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="flex items-center gap-2 font-semibold">
        <CalendarDays className="size-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Agenda
      </h2>

      {/* largura toda (embaixo da lista): mês à esquerda e datas à direita; no celular, um embaixo do outro */}
      <div className="grid gap-x-10 md:grid-cols-[18rem_minmax(0,1fr)]">
        <MonthGrid events={[...events, ...studyDays]} today={today} />
        <div>
          {todaySessions.length > 0 && (
            <>
              <h3 className="mt-5 text-xs font-semibold tracking-wide text-slate-500 uppercase md:mt-4 dark:text-slate-400">
                Estudar hoje
              </h3>
              <ul className="mt-1 divide-y divide-slate-100 dark:divide-slate-800">
                {todaySessions.map((session) => (
                  <SessionItem key={session.id} session={session} context={session.targetTitle} />
                ))}
              </ul>
            </>
          )}
          <h3 className="mt-5 text-xs md:mt-4 font-semibold tracking-wide text-slate-500 uppercase dark:text-slate-400">
            Próximas datas
          </h3>
          {events.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Nenhuma data marcada pela frente.</p>
          ) : (
            <ul className="mt-2 space-y-1">
              {events.slice(0, 8).map((event) => (
                <li key={`${event.target.id}-${event.kind}`}>
                  <Link
                    to={`/meus-concursos/${event.target.id}`}
                    className="flex gap-3 rounded-lg px-2 py-1.5 transition hover:bg-slate-50 dark:hover:bg-slate-800/60"
                  >
                    <span className="w-11 shrink-0 pt-0.5 text-sm font-semibold tabular-nums">{formatDate(event.date)}</span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-1.5 text-sm font-medium">
                        <span className={`size-2 shrink-0 rounded-full ${KIND[event.kind].dot}`} aria-hidden="true" />
                        {KIND[event.kind].label}
                        <span className="font-normal text-slate-500 dark:text-slate-400">· {countdown(event.date)}</span>
                      </span>
                      <span className="block truncate text-xs text-slate-500 dark:text-slate-400">{event.target.title}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}

          {withoutDate.length > 0 && (
            <div className="mt-4 border-t border-slate-100 pt-3 dark:border-slate-800">
              <p className="text-xs text-slate-500 dark:text-slate-400">Sem data da prova:</p>
              <ul className="mt-1 space-y-0.5">
                {withoutDate.map((target) => (
                  <li key={target.id}>
                    <Link
                      to={`/meus-concursos/${target.id}?passo=concurso`}
                      className="block truncate text-sm text-indigo-700 hover:underline dark:text-indigo-300"
                    >
                      {target.title}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}

/** Mês com marcas (uma cor por tipo de evento) nos dias com evento; começa no mês atual. */
function MonthGrid({ events, today }: { events: { date: string; kind: EventKind }[]; today: string }) {
  const [year, month] = today.split("-").map(Number);
  const [offset, setOffset] = useState(0);
  const first = new Date(Date.UTC(year, month - 1 + offset, 1));
  const shownYear = first.getUTCFullYear();
  const shownMonth = first.getUTCMonth();
  const days = new Date(Date.UTC(shownYear, shownMonth + 1, 0)).getUTCDate();
  const blanks = first.getUTCDay();

  const byDay = new Map<string, Set<EventKind>>();
  for (const event of events) {
    byDay.set(event.date, (byDay.get(event.date) ?? new Set()).add(event.kind));
  }

  return (
    <div className="mt-4">
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => setOffset((value) => value - 1)}
          disabled={offset === 0}
          aria-label="Mês anterior"
          className="grid size-8 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-100 disabled:opacity-30 dark:hover:bg-slate-800"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
        </button>
        <p className="text-sm font-semibold capitalize">{monthFormat.format(first)}</p>
        <button
          type="button"
          onClick={() => setOffset((value) => value + 1)}
          disabled={offset >= 12}
          aria-label="Próximo mês"
          className="grid size-8 place-items-center rounded-lg text-slate-500 transition hover:bg-slate-100 disabled:opacity-30 dark:hover:bg-slate-800"
        >
          <ChevronRight className="size-4" aria-hidden="true" />
        </button>
      </div>
      <div className="mt-2 grid grid-cols-7 text-center text-xs">
        {WEEKDAYS.map((weekday, index) => (
          <span key={index} className="pb-1 font-medium text-slate-400" aria-hidden="true">
            {weekday}
          </span>
        ))}
        {Array.from({ length: blanks }, (_, index) => (
          <span key={`blank-${index}`} />
        ))}
        {Array.from({ length: days }, (_, index) => {
          const day = index + 1;
          const iso = isoDate(shownYear, shownMonth, day);
          const kinds = byDay.get(iso);
          const isToday = iso === today;
          return (
            <span
              key={iso}
              title={kinds ? [...kinds].map((kind) => KIND[kind].label).join(", ") : undefined}
              className={`flex h-9 flex-col items-center justify-center rounded-lg tabular-nums ${
                isToday
                  ? "bg-indigo-600 font-semibold text-white dark:bg-indigo-500"
                  : iso < today
                    ? "text-slate-300 dark:text-slate-600"
                    : "text-slate-700 dark:text-slate-300"
              }`}
            >
              {day}
              <span className="flex h-1.5 gap-0.5">
                {kinds &&
                  [...kinds].map((kind) => (
                    <span
                      key={kind}
                      className={`size-1.5 rounded-full ${isToday ? "bg-white" : KIND[kind].dot}`}
                      aria-hidden="true"
                    />
                  ))}
              </span>
            </span>
          );
        })}
      </div>
    </div>
  );
}
