import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { type AppNotification, apiGet, apiPost, type Page } from "../api";
import { requireToken, useSession } from "./session";

export const notificationKeys = {
  all: (userId: string | undefined) => ["notifications", userId] as const,
  unread: (userId: string | undefined) => ["notifications", userId, "unread"] as const,
  list: (userId: string | undefined) => ["notifications", userId, "list"] as const,
};

/**
 * Contador do sininho. Avisos nascem na coleta (2x por dia): consultar a cada 5 minutos e ao voltar
 * para a aba é suficiente, e é uma query leve no core.
 */
export function useUnreadCount() {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: notificationKeys.unread(userId),
    queryFn: ({ signal }) =>
      apiGet<{ count: number }>("/me/notifications/unread-count", requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
    refetchInterval: 5 * 60_000,
    refetchOnWindowFocus: true,
    select: (data) => data.count,
  });
}

/** Avisos mais recentes (só quando o sininho está aberto). */
export function useNotifications(enabled: boolean) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: notificationKeys.list(userId),
    queryFn: ({ signal }) =>
      apiGet<Page<AppNotification>>("/me/notifications?size=15", requireToken(accessToken), signal),
    enabled: enabled && Boolean(accessToken),
  });
}

export function useMarkRead() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiPost(`/me/notifications/${id}/read`, requireToken(accessToken)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all(userId) }),
  });
}

export function useMarkAllRead() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiPost("/me/notifications/read-all", requireToken(accessToken)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all(userId) }),
  });
}
