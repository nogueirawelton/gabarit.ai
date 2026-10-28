import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiGet, apiPut, type CatalogPosition, type Profile } from "../api";
import { requireToken, useSession } from "./session";

export const profileKeys = {
  profile: (userId: string | undefined) => ["profile", userId] as const,
  catalog: (source: string) => ["catalog", source] as const,
};

/** Preferências de estudo do usuário logado (GET /me/profile). */
export function useProfile() {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: profileKeys.profile(userId),
    queryFn: ({ signal }) => apiGet<Profile>("/me/profile", requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
  });
}

/** Catálogo de cargos: muda no máximo 2x por dia (coleta), então fica em cache por bastante tempo. */
export function useCatalog(source = "pciconcursos") {
  const { accessToken } = useSession();
  return useQuery({
    queryKey: profileKeys.catalog(source),
    queryFn: ({ signal }) =>
      apiGet<CatalogPosition[]>(`/catalog/positions?source=${encodeURIComponent(source)}`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
    staleTime: 60 * 60_000,
    gcTime: 24 * 60 * 60_000,
  });
}

export type ProfileUpdate = { positionIds: number[]; ufs: string[] };

/** Salva as preferências e já põe a resposta no cache: a home mostra o novo sem buscar de novo. */
export function useUpdateProfile() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: ProfileUpdate) => apiPut<Profile>("/me/profile", requireToken(accessToken), update),
    onSuccess: (profile) => queryClient.setQueryData(profileKeys.profile(userId), profile),
  });
}
