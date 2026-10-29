import { keepPreviousData, useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { apiGet, type ExamDetail, type ExamStatus, type ExamSummary, isUuid, type Page } from "../api";
import { requireToken, useSession } from "./session";

export type ExamScope = "mine" | "all";

// encerrados ficam de fora da home; sem data entra (não dá para saber se acabou)
const LISTED_STATUSES: ExamStatus[] = ["OPEN", "UPCOMING", "UNKNOWN"];

const PAGE_SIZE = 20;

export const examKeys = {
  // "meus" depende do perfil: entra o usuário na chave e é invalidado quando o perfil muda
  list: (scope: ExamScope, userId: string | undefined, q: string) =>
    scope === "mine" ? (["exams", "mine", userId, q] as const) : (["exams", "all", q] as const),
  mine: ["exams", "mine"] as const,
  detail: (id: string) => ["exam", id] as const,
};

/**
 * Concursos em páginas ("Carregar mais"). Enquanto a busca muda, a lista anterior continua na tela
 * (sem piscar skeleton a cada letra).
 */
export function useExamList(scope: ExamScope, q: string, enabled = true) {
  const { userId, accessToken } = useSession();
  return useInfiniteQuery({
    queryKey: examKeys.list(scope, userId, q),
    queryFn: ({ pageParam, signal }) => {
      const params = new URLSearchParams({ page: String(pageParam), size: String(PAGE_SIZE) });
      LISTED_STATUSES.forEach((status) => params.append("status", status));
      if (q) {
        params.set("q", q);
      }
      const path = scope === "mine" ? "/me/exams" : "/exams";
      return apiGet<Page<ExamSummary>>(`${path}?${params}`, requireToken(accessToken), signal);
    },
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    placeholderData: keepPreviousData,
    enabled: enabled && Boolean(accessToken),
  });
}

export function useExam(id: string) {
  const { accessToken } = useSession();
  return useQuery({
    queryKey: examKeys.detail(id),
    queryFn: ({ signal }) => apiGet<ExamDetail>(`/exams/${id}`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken) && isUuid(id),
  });
}
