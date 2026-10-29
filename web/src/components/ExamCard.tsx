import { CalendarClock, GraduationCap, MapPin, Users } from "lucide-react";
import { Link } from "react-router";

import type { ExamStatus, ExamSummary } from "../api";
import { displayPositionName } from "../brazil";
import { statusBadge, type StatusTone } from "../exams";

const TONES: Record<StatusTone, string> = {
  urgent: "bg-amber-50 text-amber-800 dark:bg-amber-400/10 dark:text-amber-300",
  open: "bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-400",
  soon: "bg-sky-50 text-sky-700 dark:bg-sky-400/10 dark:text-sky-300",
  muted: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400",
};

/** Selo das inscrições ("Inscrições até 20/11", "Último dia de inscrição"...). */
export function StatusBadge({
  exam,
}: {
  exam: { status: ExamStatus; registrationStart: string | null; registrationEnd: string | null };
}) {
  const { label, tone } = statusBadge(exam);
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${TONES[tone]}`}>
      <CalendarClock className="size-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}

/** "SP" ou "Nacional". */
export function UfBadge({ uf }: { uf: string | null }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:border-slate-700 dark:text-slate-300">
      <MapPin className="size-3.5" aria-hidden="true" />
      {uf ?? "Nacional"}
    </span>
  );
}

const MAX_MATCHING = 3;

/** Concurso na lista: o card inteiro abre o detalhe. */
export function ExamCard({ exam }: { exam: ExamSummary }) {
  const matching = exam.matchingPositions.map(displayPositionName);
  return (
    <Link
      to={`/concursos/${exam.id}`}
      className="group flex h-full flex-col rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-indigo-300 hover:shadow-md hover:shadow-indigo-900/5 focus-visible:border-indigo-600 focus-visible:ring-4 focus-visible:ring-indigo-600/20 focus-visible:outline-none dark:border-slate-800 dark:bg-slate-900 dark:hover:border-indigo-500/50"
    >
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge exam={exam} />
        <UfBadge uf={exam.uf} />
        {exam.registrationNote && (
          <span className="text-xs font-medium text-slate-500 dark:text-slate-400">{exam.registrationNote}</span>
        )}
      </div>

      <h3 className="mt-3 line-clamp-2 font-semibold leading-snug text-slate-900 group-hover:text-indigo-700 dark:text-slate-100 dark:group-hover:text-indigo-300">
        {exam.title}
      </h3>

      <div className="mt-2 grid gap-1 text-sm text-slate-600 dark:text-slate-400">
        {exam.openings && (
          <p className="flex items-start gap-2">
            <Users className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
            {exam.openings}
          </p>
        )}
        {exam.educationLevel && (
          <p className="flex items-start gap-2">
            <GraduationCap className="mt-0.5 size-4 shrink-0 text-slate-400" aria-hidden="true" />
            {exam.educationLevel}
          </p>
        )}
      </div>

      {matching.length > 0 && (
        // empurra para o rodapé do card: cards da mesma linha alinham embaixo
        <p className="mt-auto pt-4 text-sm">
          <span className="text-slate-500 dark:text-slate-400">Seus cargos: </span>
          <span className="font-medium text-indigo-700 dark:text-indigo-300">
            {matching.slice(0, MAX_MATCHING).join(", ")}
            {matching.length > MAX_MATCHING && ` e mais ${matching.length - MAX_MATCHING}`}
          </span>
        </p>
      )}
    </Link>
  );
}
