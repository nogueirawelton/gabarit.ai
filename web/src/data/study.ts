import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  apiDelete,
  apiGet,
  apiPostJson,
  apiPut,
  isUuid,
  type StudyDocument,
  type StudyDocumentKind,
  type StudyMaterials,
  type StudyPaper,
  type StudyTargetDetail,
  type StudyTargetSummary,
  type StudyTargetUpdate,
} from "../api";
import { requireToken, useSession } from "./session";

export const studyKeys = {
  all: (userId: string | undefined) => ["study-targets", userId] as const,
  list: (userId: string | undefined) => ["study-targets", userId, "list"] as const,
  detail: (userId: string | undefined, id: string) => ["study-targets", userId, id] as const,
};

/** Carteira: concursos que o usuário estuda, mais recentes primeiro. */
export function useStudyTargets() {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: studyKeys.list(userId),
    queryFn: ({ signal }) => apiGet<StudyTargetSummary[]>("/me/study-targets", requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
  });
}

export function useStudyTarget(id: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: studyKeys.detail(userId, id),
    queryFn: ({ signal }) => apiGet<StudyTargetDetail>(`/me/study-targets/${id}`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken) && isUuid(id),
  });
}

/** Concurso da lista (`examId`) ou cadastrado à mão (`title`). Já estando na carteira, devolve o existente. */
export function useAddStudyTarget() {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { examId: string } | { title: string }) =>
      apiPostJson<StudyTargetDetail>("/me/study-targets", requireToken(accessToken), input),
    onSuccess: (target) => {
      queryClient.setQueryData(studyKeys.detail(userId, target.id), target);
      void queryClient.invalidateQueries({ queryKey: studyKeys.list(userId) });
    },
  });
}

export function useUpdateStudyTarget(id: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: StudyTargetUpdate) =>
      apiPut<StudyTargetDetail>(`/me/study-targets/${id}`, requireToken(accessToken), update),
    onSuccess: (target) => {
      queryClient.setQueryData(studyKeys.detail(userId, id), target);
      void queryClient.invalidateQueries({ queryKey: studyKeys.list(userId) });
    },
  });
}

export function useRemoveStudyTarget(id: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiDelete(`/me/study-targets/${id}`, requireToken(accessToken)),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: studyKeys.detail(userId, id) });
      void queryClient.invalidateQueries({ queryKey: studyKeys.list(userId) });
    },
  });
}

export const materialKeys = {
  list: (userId: string | undefined, targetId: string) => ["study-targets", userId, targetId, "materials"] as const,
};

/** Tamanho máximo aceito pelo core (a API do Claude lê até 32 MB por requisição). */
export const MAX_DOCUMENT_BYTES = 30 * 1024 * 1024;

/** Editais e provas relacionadas (cada uma com a prova e o gabarito). */
export function useStudyMaterials(targetId: string) {
  const { userId, accessToken } = useSession();
  return useQuery({
    queryKey: materialKeys.list(userId, targetId),
    queryFn: ({ signal }) =>
      apiGet<StudyMaterials>(`/me/study-targets/${targetId}/materials`, requireToken(accessToken), signal),
    enabled: Boolean(accessToken),
    // edital (ou o conteúdo do cargo) sendo lido pela IA: confere de tempos em tempos até terminar
    refetchInterval: (query) =>
      query.state.data?.editais.some(
        (edital) => edital.reading?.status === "PENDING" || edital.syllabus?.status === "PENDING",
      ) || query.state.data?.papers.some((paper) => paper.reading?.status === "PENDING")
        ? 5000
        : false,
  });
}

function useInvalidateMaterials(targetId: string) {
  const { userId } = useSession();
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: materialKeys.list(userId, targetId) });
}

export function useAddPaper(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (paper: { year: number | null; variant: string | null; answersIncluded: boolean }) =>
      apiPostJson<StudyPaper>(`/me/study-targets/${targetId}/papers`, requireToken(accessToken), paper),
    onSuccess: invalidate,
  });
}

export function useRemovePaper(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (paperId: string) =>
      apiDelete(`/me/study-targets/${targetId}/papers/${paperId}`, requireToken(accessToken)),
    onSuccess: invalidate,
  });
}

/** PUT direto no MinIO com progresso (o `fetch` não informa progresso de envio; o XHR, sim). */
function putWithProgress(url: string, file: File, contentType: string, onProgress: (fraction: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", contentType);
    xhr.upload.onprogress = (event) => event.lengthComputable && onProgress(event.loaded / event.total);
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`envio: ${xhr.status}`)));
    xhr.onerror = () => reject(new Error("envio: falha de rede"));
    xhr.send(file);
  });
}

/**
 * Envio em três passos: o core entrega a URL, o navegador manda o PDF direto ao MinIO e confirma, e o
 * core confere o arquivo. Edital sem `paperId`; prova e gabarito no espaço da prova relacionada.
 * Devolve o arquivo já conferido (`READY` ou `REJECTED` com o motivo).
 */
export function useUploadDocument(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: async ({
      file,
      kind,
      paperId,
      onProgress,
    }: {
      file: File;
      kind: StudyDocumentKind;
      paperId?: string;
      onProgress: (fraction: number) => void;
    }) => {
      const token = requireToken(accessToken);
      const started = await apiPostJson<{ document: StudyDocument; uploadUrl: string; contentType: string }>(
        `/me/study-targets/${targetId}/documents`,
        token,
        { kind, paperId: paperId ?? null, name: file.name, size: file.size },
      );
      await putWithProgress(started.uploadUrl, file, started.contentType, onProgress);
      return apiPostJson<StudyDocument>(
        `/me/study-targets/${targetId}/documents/${started.document.id}/complete`,
        token,
        undefined,
      );
    },
    onSettled: invalidate,
  });
}

/** Pede (de novo) a leitura do edital do concurso pela IA. */
export function useReadEdital(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (documentId: string) =>
      apiPostJson<StudyDocument>(
        `/me/study-targets/${targetId}/documents/${documentId}/reading`,
        requireToken(accessToken),
        undefined,
      ),
    onSuccess: invalidate,
  });
}

/**
 * Escolhe o cargo entre os da leitura do edital (só grava; o conteúdo é pedido à parte, com
 * {@link useReadSyllabus}). No concurso cadastrado à mão ele vira também o cargo do concurso; no
 * da lista, o cargo do concurso continua o coletado.
 */
export function useChooseEditalPosition(targetId: string) {
  const { userId, accessToken } = useSession();
  const queryClient = useQueryClient();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: ({ documentId, ...position }: { documentId: string; name: string; specialty: string | null }) =>
      apiPut<StudyDocument>(
        `/me/study-targets/${targetId}/documents/${documentId}/position`,
        requireToken(accessToken),
        position,
      ),
    onSuccess: () => {
      void invalidate();
      void queryClient.invalidateQueries({ queryKey: studyKeys.detail(userId, targetId) });
      void queryClient.invalidateQueries({ queryKey: studyKeys.list(userId) });
    },
  });
}

/** Pede a leitura do conteúdo programático do cargo escolhido (ou de novo, depois de falha passageira). */
export function useReadSyllabus(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (documentId: string) =>
      apiPostJson<StudyDocument>(
        `/me/study-targets/${targetId}/documents/${documentId}/syllabus`,
        requireToken(accessToken),
        undefined,
      ),
    onSuccess: invalidate,
  });
}

/** Pede a leitura das questões da prova relacionada (prova + gabarito), ou de novo. */
export function useReadPaper(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (paperId: string) =>
      apiPostJson<StudyPaper>(`/me/study-targets/${targetId}/papers/${paperId}/reading`, requireToken(accessToken), undefined),
    onSuccess: invalidate,
  });
}

export function useRemoveDocument(targetId: string) {
  const { accessToken } = useSession();
  const invalidate = useInvalidateMaterials(targetId);
  return useMutation({
    mutationFn: (documentId: string) =>
      apiDelete(`/me/study-targets/${targetId}/documents/${documentId}`, requireToken(accessToken)),
    onSuccess: invalidate,
  });
}
