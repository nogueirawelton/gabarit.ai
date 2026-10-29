import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  apiDelete,
  apiGet,
  apiPostForm,
  apiPut,
  type CatalogPosition,
  type EducationLevel,
  type Profile,
} from "../api";
import { examKeys } from "./exams";
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

export type ProfileUpdate = { positionIds: string[]; ufs: string[]; educationLevels: EducationLevel[] };

/**
 * Salva as preferências e já põe a resposta no cache: a home mostra o novo sem buscar de novo.
 * "Meus concursos" dependem dos cargos e UFs: são buscados de novo.
 */
export function useUpdateProfile() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: ProfileUpdate) => apiPut<Profile>("/me/profile", requireToken(accessToken), update),
    onSuccess: (profile) => {
      queryClient.setQueryData(profileKeys.profile(userId), profile);
      void queryClient.invalidateQueries({ queryKey: examKeys.mine });
    },
  });
}

/** Envia a foto (já preparada por `prepareAvatar`) e troca a URL no cache: cabeçalho e perfil atualizam juntos. */
export function useUploadAvatar() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (image: Blob) => {
      const form = new FormData();
      form.append("file", image, "avatar.jpg");
      return apiPostForm<{ url: string }>("/me/avatar", requireToken(accessToken), form);
    },
    onSuccess: ({ url }) => setAvatarUrl(queryClient, userId, url),
  });
}

export function useRemoveAvatar() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiDelete("/me/avatar", requireToken(accessToken)),
    onSuccess: () => setAvatarUrl(queryClient, userId, null),
  });
}

function setAvatarUrl(queryClient: QueryClient, userId: string | undefined, avatarUrl: string | null) {
  const key = profileKeys.profile(userId);
  if (queryClient.getQueryData<Profile>(key)) {
    queryClient.setQueryData<Profile>(key, (profile) => profile && { ...profile, avatarUrl });
  } else {
    void queryClient.invalidateQueries({ queryKey: key });
  }
}

/** Liga ou desliga o resumo diário por e-mail; salva na hora (sem a barra de salvar). */
export function useChangeEmailDigest() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (emailDigest: boolean) =>
      apiPut<Profile>("/me/profile/email-digest", requireToken(accessToken), { emailDigest }),
    onSuccess: (profile) => queryClient.setQueryData(profileKeys.profile(userId), profile),
  });
}
