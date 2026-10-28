import { QueryClient } from "@tanstack/react-query";

import { ApiError } from "../api";

/**
 * Cache dos dados da API. Voltar para uma tela mostra o que já foi carregado na hora e atualiza
 * em segundo plano (stale-while-revalidate): skeleton só no primeiro carregamento.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // dentro desse tempo, voltar para a tela nem chama a API
      staleTime: 30_000,
      // erro do cliente (400, 401, 403, 404) não melhora tentando de novo; rede/5xx tenta até 2 vezes
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status < 500) && failureCount < 2,
    },
  },
});
