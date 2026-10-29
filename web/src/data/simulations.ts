import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  apiGet,
  apiPostJson,
  apiPut,
  isUuid,
  type ReportReason,
  type Simulation,
  type SimulationSummary,
} from "../api";
import { requireToken, useSession } from "./session";

export const simulationKeys = {
  list: (userId: string | undefined, targetId: string) => ["simulations", userId, targetId] as const,
  detail: (userId: string | undefined, id: string) => ["simulation", userId, id] as const,
};

/** Simulados do concurso, mais recentes primeiro. */
export function useSimulations(targetId: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: simulationKeys.list(userId, targetId),
    queryFn: ({ signal }) =>
      apiGet<SimulationSummary[]>(`/me/study-targets/${targetId}/simulations`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
  });
}

export function useSimulation(id: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: simulationKeys.detail(userId, id),
    queryFn: ({ signal }) => apiGet<Simulation>(`/me/simulations/${id}`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken) && isUuid(id),
    // a prova não muda por fora: só recarrega quando a pessoa age
    refetchOnWindowFocus: false,
  });
}

export function useStartSimulation(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { paperId: string; timeLimitMinutes: number | null }) =>
      apiPostJson<Simulation>(`/me/study-targets/${targetId}/simulations`, requireToken(accessToken), input),
    onSuccess: (simulation) => {
      queryClient.setQueryData(simulationKeys.detail(userId, simulation.id), simulation);
      void queryClient.invalidateQueries({ queryKey: simulationKeys.list(userId, targetId) });
    },
  });
}

/**
 * Marca (ou desmarca, com null) a resposta. A tela muda na hora; se o servidor recusar (tempo
 * esgotado), volta ao que estava.
 */
export function useAnswer(simulationId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  const key = simulationKeys.detail(userId, simulationId);
  return useMutation({
    mutationFn: ({ questionId, answer }: { questionId: string; answer: string | null }) =>
      apiPut<void>(`/me/simulations/${simulationId}/answers/${questionId}`, requireToken(accessToken), { answer }),
    onMutate: async ({ questionId, answer }) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<Simulation>(key);
      queryClient.setQueryData<Simulation>(key, (current) =>
        current && {
          ...current,
          questions: current.questions.map((q) => (q.id === questionId ? { ...q, chosen: answer } : q)),
        },
      );
      return { previous };
    },
    onError: (_error, _input, context) => {
      if (context?.previous) {
        queryClient.setQueryData(key, context.previous);
      }
    },
  });
}

export function useFinishSimulation(simulationId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiPostJson<Simulation>(`/me/simulations/${simulationId}/finish`, requireToken(accessToken), undefined),
    onSuccess: (simulation) => {
      queryClient.setQueryData(simulationKeys.detail(userId, simulation.id), simulation);
      void queryClient.invalidateQueries({ queryKey: ["simulations", userId] });
    },
  });
}

/** Reporta uma questão com problema; o simulado volta do servidor (a nota muda). */
export function useReportQuestion(simulationId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ questionId, reason, comment }: { questionId: string; reason: ReportReason; comment: string }) =>
      apiPut<void>(`/me/simulations/${simulationId}/questions/${questionId}/report`, requireToken(accessToken), {
        reason,
        comment: comment || null,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: simulationKeys.detail(userId, simulationId) }),
  });
}
