// Datas puras ("2026-11-10"), no fuso do navegador, sem `new Date` em texto ISO (que vira UTC e
// pode mudar de dia).

export function isoDate(year: number, month: number, day: number) {
  return `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** Hoje no fuso do navegador. */
export function todayIso() {
  const now = new Date();
  return isoDate(now.getFullYear(), now.getMonth(), now.getDate());
}

export function addDays(iso: string, days: number) {
  const [year, month, day] = iso.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return isoDate(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
}

/** Segunda-feira da semana da data. */
export function mondayOf(iso: string) {
  const [year, month, day] = iso.split("-").map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return addDays(iso, -((weekday + 6) % 7));
}

/** 90 → "1h30", 60 → "1h", 45 → "45 min". */
export function formatMinutes(minutes: number) {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest} min`;
  }
  return rest === 0 ? `${hours}h` : `${hours}h${String(rest).padStart(2, "0")}`;
}
