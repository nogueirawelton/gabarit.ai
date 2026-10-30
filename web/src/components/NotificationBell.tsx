import { Bell, BellOff } from "lucide-react";
import { useNavigate } from "react-router";

import type { AppNotification, StudyNotificationType } from "../api";
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "../data/notifications";
import { statusBadge } from "../exams";
import { useMenu } from "./useMenu";

const KIND_LABEL: Record<AppNotification["type"], string> = {
  NEW_EXAM: "Concurso novo",
  EDITAL_READ: "Edital lido",
  EDITAL_FAILED: "Edital",
  SYLLABUS_READ: "Conteúdo lido",
  SYLLABUS_FAILED: "Conteúdo",
  PAPER_READ: "Prova lida",
  PAPER_FAILED: "Prova",
  QUESTIONS_READY: "Questões prontas",
};

/** O que fazer com o aviso de um concurso da carteira, e em que passo ele abre. */
const STUDY_NOTICE: Record<StudyNotificationType, { text: string; step: string }> = {
  EDITAL_READ: { text: "Confira os dados e escolha o seu cargo", step: "edital" },
  EDITAL_FAILED: { text: "Não conseguimos ler o edital. Veja na página do concurso", step: "edital" },
  SYLLABUS_READ: { text: "O conteúdo programático do seu cargo está pronto", step: "cargo" },
  SYLLABUS_FAILED: { text: "Não conseguimos ler o conteúdo do seu cargo. Tente de novo", step: "cargo" },
  PAPER_READ: { text: "As questões da prova relacionada foram lidas", step: "provas" },
  PAPER_FAILED: { text: "Não conseguimos ler uma prova relacionada. Veja no passo Provas", step: "provas" },
  QUESTIONS_READY: { text: "As questões geradas estão prontas: já dá para fazer o simulado inédito", step: "estudar" },
};

const relative = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });

/** "há 5 minutos", "ontem", "há 3 dias". */
function timeAgo(iso: string): string {
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  const minutes = Math.round(seconds / 60);
  const hours = Math.round(minutes / 60);
  const days = Math.round(hours / 24);
  if (Math.abs(minutes) < 1) return "agora";
  if (Math.abs(hours) < 1) return relative.format(minutes, "minute");
  if (Math.abs(days) < 1) return relative.format(hours, "hour");
  return relative.format(days, "day");
}

/**
 * Sininho: avisos de concurso novo para os cargos do usuário e de leituras prontas dos concursos da carteira (edital,
 * conteúdo do cargo, provas relacionadas, questões geradas). O contador atualiza sozinho; a lista
 * só é buscada ao abrir. Abrir um aviso marca como lido e vai para o concurso.
 */
export function NotificationBell() {
  const { open, setOpen, root, trigger, menu, onMenuKeyDown } = useMenu();
  const unread = useUnreadCount();
  const list = useNotifications(open);
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();
  const navigate = useNavigate();
  const count = unread.data ?? 0;

  function openNotification(notification: AppNotification) {
    if (!notification.read) {
      markRead.mutate(notification.id);
    }
    void navigate(
      notification.type === "NEW_EXAM"
        ? `/concursos/${notification.exam.id}`
        : `/meus-concursos/${notification.studyTarget.id}?passo=${STUDY_NOTICE[notification.type].step}`,
    );
  }

  return (
    <div ref={root} className="relative">
      <button
        ref={trigger}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={count > 0 ? `Avisos, ${count} não lidos` : "Avisos"}
        className={`relative grid size-10 place-items-center rounded-xl text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-100 ${
          open ? "bg-slate-100 dark:bg-slate-900" : ""
        }`}
      >
        <Bell className="size-5" aria-hidden="true" />
        {count > 0 && (
          <span
            aria-hidden="true"
            className="absolute top-1 right-1 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] leading-none font-bold text-white ring-2 ring-white dark:ring-slate-950"
          >
            {count > 99 ? "99+" : count}
          </span>
        )}
      </button>

      {open && (
        <div
          ref={menu}
          role="menu"
          aria-label="Avisos"
          onKeyDown={onMenuKeyDown}
          // no celular ocupa a largura da tela (painel de 24rem não cabe ancorado no sino)
          className="fixed inset-x-4 top-[4.5rem] max-h-[min(32rem,calc(100svh-7rem))] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg shadow-slate-900/10 sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96 dark:border-slate-800 dark:bg-slate-900 dark:shadow-black/40"
        >
          <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
            <p className="text-sm font-semibold">Avisos</p>
            {count > 0 && (
              <button
                type="button"
                role="menuitem"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                className="rounded text-xs font-semibold text-indigo-600 outline-none hover:underline focus-visible:underline disabled:opacity-60 dark:text-indigo-400"
              >
                Marcar todos como lidos
              </button>
            )}
          </div>

          <div className="max-h-[min(28rem,calc(100svh-10rem))] overflow-y-auto">
            {list.isPending ? (
              <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400" role="status">
                Carregando…
              </p>
            ) : list.isError ? (
              <p className="px-4 py-6 text-sm text-red-700 dark:text-red-300">Não foi possível carregar os avisos.</p>
            ) : list.data.items.length === 0 ? (
              <div className="px-6 py-10 text-center">
                <BellOff className="mx-auto size-6 text-slate-400" aria-hidden="true" />
                <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
                  Nenhum aviso ainda. Quando abrir concurso para os seus cargos, ele aparece aqui.
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                {list.data.items.map((notification) => (
                  <li key={notification.id}>
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => openNotification(notification)}
                      className="flex w-full gap-3 px-4 py-3 text-left outline-none transition hover:bg-slate-50 focus-visible:bg-slate-50 dark:hover:bg-slate-800/60 dark:focus-visible:bg-slate-800/60"
                    >
                      <span
                        aria-hidden="true"
                        className={`mt-1.5 size-2 shrink-0 rounded-full ${notification.read ? "bg-transparent" : "bg-indigo-500"}`}
                      />
                      <span className="min-w-0">
                        <span className="block text-xs text-slate-500 dark:text-slate-400">
                          {KIND_LABEL[notification.type]} · {timeAgo(notification.createdAt)}
                          {!notification.read && <span className="sr-only"> (não lido)</span>}
                        </span>
                        <span
                          className={`mt-0.5 line-clamp-2 block text-sm ${notification.read ? "text-slate-600 dark:text-slate-400" : "font-semibold text-slate-900 dark:text-slate-100"}`}
                        >
                          {notification.type === "NEW_EXAM" ? notification.exam.title : notification.studyTarget.title}
                        </span>
                        <span className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">
                          {notification.type === "NEW_EXAM"
                            ? `${notification.exam.uf ?? "Nacional"} · ${statusBadge(notification.exam).label}`
                            : STUDY_NOTICE[notification.type].text}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
