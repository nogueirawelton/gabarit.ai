import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiGet, apiPostJson, apiPut, type AgendaSession, type Simulation, type StudyPlan } from "../api";
import { simulationKeys } from "./simulations";
import { requireToken, useSession } from "./session";

const planKeys = {
  plan: (userId: string | undefined, targetId: string) => ["plan", userId, targetId] as const,
  agenda: (userId: string | undefined, from: string, to: string) => ["plan-sessions", userId, from, to] as const,
};

/** O plano do concurso; null se ainda não tem (a API responde 204). */
export function usePlan(targetId: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: planKeys.plan(userId, targetId),
    queryFn: async ({ signal }) =>
      (await apiGet<StudyPlan | undefined>(`/me/study-targets/${targetId}/plan`, requireToken(accessToken), signal)) ??
      null,
    enabled: Boolean(accessToken),
  });
}

/** Monta ou refaz o plano (as sessões feitas ficam). */
export function useSavePlan(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { endDate: string; dailyMinutes: number[] }) =>
      apiPut<StudyPlan>(`/me/study-targets/${targetId}/plan`, requireToken(accessToken), input),
    onSuccess: (plan) => {
      queryClient.setQueryData(planKeys.plan(userId, targetId), plan);
      void queryClient.invalidateQueries({ queryKey: ["plan-sessions", userId] });
    },
  });
}

/** Marca (ou desmarca) uma sessão como feita; a tela muda na hora. */
export function useSessionDone() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, done }: { sessionId: string; done: boolean }) =>
      apiPut<void>(`/me/plan-sessions/${sessionId}`, requireToken(accessToken), { done }),
    onMutate: ({ sessionId, done }) => {
      const mark = <T extends { id: string; done: boolean }>(session: T) =>
        session.id === sessionId ? { ...session, done } : session;
      queryClient.setQueriesData<StudyPlan | null>({ queryKey: ["plan", userId] }, (plan) =>
        plan ? { ...plan, sessions: plan.sessions.map(mark) } : plan,
      );
      queryClient.setQueriesData<AgendaSession[]>({ queryKey: ["plan-sessions", userId] }, (sessions) =>
        sessions?.map(mark),
      );
    },
    // o resumo por disciplina (minutos feitos) vem do servidor
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["plan", userId] }),
  });
}

/** Começa uma prática (simulado sem tempo) com as questões dos tópicos da sessão. */
export function usePractice() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiPostJson<Simulation>(`/me/plan-sessions/${sessionId}/practice`, requireToken(accessToken), undefined),
    onSuccess: (simulation) => {
      queryClient.setQueryData(simulationKeys.detail(userId, simulation.id), simulation);
      void queryClient.invalidateQueries({ queryKey: ["simulations", userId] });
    },
  });
}

/** Sessões de todos os concursos no período (datas ISO, inclusivas). */
export function useAgendaSessions(from: string, to: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: planKeys.agenda(userId, from, to),
    queryFn: ({ signal }) =>
      apiGet<AgendaSession[]>(`/me/plan-sessions?from=${from}&to=${to}`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
  });
}
