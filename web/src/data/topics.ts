import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  apiGet,
  apiPostJson,
  type GenerationState,
  type MockExamStatus,
  type PaperTopics,
  type Simulation,
  type TopicPractice,
  type TopicSummary,
  type TopicsOverview,
} from "../api";
import { simulationKeys } from "./simulations";
import { requireToken, useSession } from "./session";

const topicKeys = {
  overview: (userId: string | undefined, targetId: string) => ["topics", userId, targetId] as const,
  practice: (userId: string | undefined, targetId: string) => ["topic-practice", userId, targetId] as const,
  mockExam: (userId: string | undefined, targetId: string) => ["mock-exam", userId, targetId] as const,
  summary: (userId: string | undefined, targetId: string, subject: number, topic: number) =>
    ["topic-summary", userId, targetId, subject, topic] as const,
};

/** O que mais cai (questões por tópico) e a ligação de cada prova; confere enquanto alguma liga. */
export function useTopics(targetId: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: topicKeys.overview(userId, targetId),
    queryFn: ({ signal }) =>
      apiGet<TopicsOverview>(`/me/study-targets/${targetId}/topics`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
    refetchInterval: (query) => (query.state.data?.papers.some((paper) => paper.status === "PENDING") ? 5000 : false),
  });
}

/** Pede a ligação das questões da prova aos tópicos do conteúdo do cargo (ou de novo). */
export function useClassifyPaper(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (paperId: string) =>
      apiPostJson<PaperTopics>(`/me/study-targets/${targetId}/papers/${paperId}/topics`, requireToken(accessToken), undefined),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: topicKeys.overview(userId, targetId) }),
  });
}

/** Cada tópico com as questões para praticar e a geração; confere enquanto alguma gera. */
export function useTopicPractice(targetId: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: topicKeys.practice(userId, targetId),
    queryFn: ({ signal }) =>
      apiGet<TopicPractice[]>(`/me/study-targets/${targetId}/topic-practice`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
    refetchInterval: (query) => (query.state.data?.some((t) => t.generation?.status === "PENDING") ? 5000 : false),
  });
}

/** Gera questões inéditas para o tópico, no estilo da banca. */
export function useGenerateTopic(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ subject, topic }: { subject: number; topic: number }) =>
      apiPostJson<GenerationState>(
        `/me/study-targets/${targetId}/topics/${subject}/${topic}/generate`,
        requireToken(accessToken),
        undefined,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: topicKeys.practice(userId, targetId) }),
  });
}

/** Prática (simulado sem tempo) do tópico. */
export function usePracticeTopic(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ subject, topic }: { subject: number; topic: number }) =>
      apiPostJson<Simulation>(
        `/me/study-targets/${targetId}/topics/${subject}/${topic}/practice`,
        requireToken(accessToken),
        undefined,
      ),
    onSuccess: (simulation) => {
      queryClient.setQueryData(simulationKeys.detail(userId, simulation.id), simulation);
      void queryClient.invalidateQueries({ queryKey: ["simulations", userId] });
    },
  });
}

/** O simulado inédito do cargo (estrutura e preparo); confere enquanto prepara. Só com o conteúdo lido. */
export function useMockExam(targetId: string, enabled: boolean) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: topicKeys.mockExam(userId, targetId),
    queryFn: ({ signal }) =>
      apiGet<MockExamStatus>(`/me/study-targets/${targetId}/mock-exam`, requireToken(accessToken), signal),
    enabled: enabled && Boolean(accessToken),
    refetchInterval: (query) => ((query.state.data?.pendingTopics ?? 0) > 0 ? 5000 : false),
  });
}

/** Gera as questões que faltam para o simulado inédito. */
export function usePrepareMockExam(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiPostJson<MockExamStatus>(`/me/study-targets/${targetId}/mock-exam/prepare`, requireToken(accessToken), undefined),
    onSuccess: (status) => {
      queryClient.setQueryData(topicKeys.mockExam(userId, targetId), status);
      void queryClient.invalidateQueries({ queryKey: topicKeys.practice(userId, targetId) });
    },
  });
}

/** Começa o simulado inédito. */
export function useStartMockExam(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { timeLimitMinutes: number | null }) =>
      apiPostJson<Simulation>(`/me/study-targets/${targetId}/mock-exam/start`, requireToken(accessToken), input),
    onSuccess: (simulation) => {
      queryClient.setQueryData(simulationKeys.detail(userId, simulation.id), simulation);
      void queryClient.invalidateQueries({ queryKey: ["simulations", userId] });
    },
  });
}

/** Resumo do tópico (null se ainda não pedido); confere enquanto é gerado. */
export function useTopicSummary(targetId: string, subject: number, topic: number, enabled: boolean) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: topicKeys.summary(userId, targetId, subject, topic),
    queryFn: async ({ signal }) =>
      (await apiGet<TopicSummary | undefined>(
        `/me/study-targets/${targetId}/topics/${subject}/${topic}/summary`,
        requireToken(accessToken),
        signal,
      )) ?? null,
    enabled: enabled && Boolean(accessToken),
    refetchInterval: (query) => (query.state.data?.status === "PENDING" ? 5000 : false),
  });
}

/** Pede o resumo do tópico pela IA. */
export function useRequestSummary(targetId: string, subject: number, topic: number) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiPostJson<TopicSummary>(
        `/me/study-targets/${targetId}/topics/${subject}/${topic}/summary`,
        requireToken(accessToken),
        undefined,
      ),
    onSuccess: (summary) => queryClient.setQueryData(topicKeys.summary(userId, targetId, subject, topic), summary),
  });
}
