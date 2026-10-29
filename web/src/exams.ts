import type { ExamStatus } from "./api";

/** "2026-11-20" → "20/11" (ou "20/11/2026"). Sem `new Date`: data pura não pode mudar de dia pelo fuso. */
export function formatDate(iso: string, withYear = false): string {
  const [year, month, day] = iso.split("-");
  return withYear ? `${day}/${month}/${year}` : `${day}/${month}`;
}

/** Dias de hoje (no fuso do navegador) até a data: 0 = hoje. */
function daysUntil(iso: string): number {
  const [year, month, day] = iso.split("-").map(Number);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((Date.UTC(year, month - 1, day) - today) / 86_400_000);
}

export type StatusTone = "urgent" | "open" | "soon" | "muted";

/** Texto e cor do selo de inscrições. Perto do fim vira urgente: é o que faz a pessoa agir. */
export function statusBadge(exam: {
  status: ExamStatus;
  registrationStart: string | null;
  registrationEnd: string | null;
}): { label: string; tone: StatusTone } {
  switch (exam.status) {
    case "OPEN": {
      if (!exam.registrationEnd) {
        return { label: "Inscrições abertas", tone: "open" };
      }
      const days = daysUntil(exam.registrationEnd);
      if (days <= 0) {
        return { label: "Último dia de inscrição", tone: "urgent" };
      }
      if (days <= 3) {
        return { label: `Inscrições terminam em ${days} ${days === 1 ? "dia" : "dias"}`, tone: "urgent" };
      }
      return { label: `Inscrições até ${formatDate(exam.registrationEnd)}`, tone: "open" };
    }
    case "UPCOMING":
      return {
        label: exam.registrationStart ? `Inscrições a partir de ${formatDate(exam.registrationStart)}` : "Em breve",
        tone: "soon",
      };
    case "CLOSED":
      return {
        label: exam.registrationEnd ? `Encerrado em ${formatDate(exam.registrationEnd)}` : "Encerrado",
        tone: "muted",
      };
    case "UNKNOWN":
      return { label: "Sem data de inscrição", tone: "muted" };
  }
}

/** Período de inscrição por extenso, para o detalhe. */
export function registrationPeriod(start: string | null, end: string | null): string {
  if (start && end) {
    return `${formatDate(start)} a ${formatDate(end, true)}`;
  }
  if (end) {
    return `Até ${formatDate(end, true)}`;
  }
  if (start) {
    return `A partir de ${formatDate(start, true)}`;
  }
  return "Não informado";
}
