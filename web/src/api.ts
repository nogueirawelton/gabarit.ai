import { config } from "./config";

/** Ids da API são UUID: endereço com outra coisa nem chega a consultar. */
export function isUuid(value: string | undefined): value is string {
  return Boolean(value && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value));
}

/** Erro da API. `detail` vem do ProblemDetail do core, quando houver. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail?: string,
  ) {
    super(detail ?? `API respondeu ${status}`);
  }
}

async function request<T>(method: string, path: string, accessToken: string, body?: unknown, signal?: AbortSignal) {
  // FormData (upload): o navegador põe o Content-Type multipart com o boundary
  const form = body instanceof FormData;
  const response = await fetch(`${config.apiUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(body !== undefined && !form && { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
    signal,
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new ApiError(response.status, problem?.detail);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** GET na API do core com o access token do Keycloak. */
export function apiGet<T>(path: string, accessToken: string, signal?: AbortSignal): Promise<T> {
  return request<T>("GET", path, accessToken, undefined, signal);
}

export function apiPut<T>(path: string, accessToken: string, body: unknown): Promise<T> {
  return request<T>("PUT", path, accessToken, body);
}

/** POST multipart (upload de arquivo). */
export function apiPostForm<T>(path: string, accessToken: string, form: FormData): Promise<T> {
  return request<T>("POST", path, accessToken, form);
}

export function apiDelete(path: string, accessToken: string): Promise<void> {
  return request<void>("DELETE", path, accessToken);
}

/** POST com corpo JSON e resposta JSON. */
export function apiPostJson<T>(path: string, accessToken: string, body: unknown): Promise<T> {
  return request<T>("POST", path, accessToken, body);
}

/** POST sem corpo e sem resposta (ações, ex.: marcar aviso como lido). */
export function apiPost(path: string, accessToken: string): Promise<void> {
  return request<void>("POST", path, accessToken);
}

/** Usuário logado, segundo o core (GET /me). */
export type Me = {
  id: string;
  email: string | null;
  name: string | null;
  givenName: string | null;
};

/** Cargo do catálogo (GET /catalog/positions). */
export type CatalogPosition = {
  id: string;
  slug: string;
  name: string;
};

/**
 * Preferências de estudo (GET/PUT /me/profile). `avatarUrl`: URL temporária da foto, lida direto
 * do MinIO (sem token); null = sem foto.
 */
export type Profile = {
  positions: CatalogPosition[];
  ufs: string[];
  /** Níveis de vaga que interessam; vazio = todos. */
  educationLevels: EducationLevel[];
  avatarUrl: string | null;
  /** Resumo diário de concursos novos por e-mail (o sininho no app vale sempre). */
  emailDigest: boolean;
};

export type EducationLevel = "FUNDAMENTAL" | "MEDIO" | "TECNICO" | "SUPERIOR";

/** Situação das inscrições hoje, calculada pelo core (fuso de Brasília). */
export type ExamStatus = "UPCOMING" | "OPEN" | "CLOSED" | "UNKNOWN";

/** Concurso na listagem (GET /exams, GET /me/exams). Datas em ISO (`2026-11-20`), sem hora. */
export type ExamSummary = {
  id: string;
  title: string;
  uf: string | null;
  region: string | null;
  educationLevel: string | null;
  openings: string | null;
  registrationStart: string | null;
  registrationEnd: string | null;
  registrationNote: string | null;
  status: ExamStatus;
  url: string;
  /** Cargos do concurso que o usuário segue (só em /me/exams). */
  matchingPositions: string[];
};

/** Concurso com os cargos (GET /exams/{id}). */
export type ExamDetail = Omit<ExamSummary, "matchingPositions"> & {
  positions: { id: string; name: string }[];
};

/** Página da API (PageResponse do core). */
export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/** Aviso de concurso novo para os cargos do usuário (GET /me/notifications). */
export type AppNotification = {
  id: string;
  createdAt: string;
  read: boolean;
  exam: ExamSummary;
};

/** Concurso na carteira (GET /me/study-targets). `exam`: o concurso da lista, se veio dela. */
export type StudyTargetSummary = {
  id: string;
  examId: string | null;
  title: string;
  organ: string | null;
  board: string | null;
  year: number | null;
  positionName: string | null;
  /** Data da prova, ISO (`2027-01-20`). */
  examDate: string | null;
  createdAt: string;
  exam: ExamSummary | null;
};

/**
 * Concurso da carteira aberto: o concurso da lista vem com os cargos (para escolher o seu).
 * `suggestedPositions`: cargos do concurso que a pessoa segue no perfil (aparecem primeiro).
 */
export type StudyTargetDetail = Omit<StudyTargetSummary, "exam"> & {
  exam: ExamDetail | null;
  suggestedPositions: string[];
};

/** Campos editáveis (o PUT manda todos; vazio apaga). */
export type StudyTargetUpdate = {
  title: string;
  organ: string | null;
  board: string | null;
  year: number | null;
  positionName: string | null;
  examDate: string | null;
};

export type StudyDocumentKind = "EDITAL" | "PROVA" | "GABARITO";

/** Arquivo de um concurso da carteira. `url`: abre o PDF (só `READY`; expira em 1h). */
export type StudyDocument = {
  id: string;
  kind: StudyDocumentKind;
  originalName: string;
  status: "UPLOADING" | "READY" | "REJECTED";
  rejectionReason: string | null;
  sizeBytes: number | null;
  pages: number | null;
  createdAt: string;
  url: string | null;
};

/**
 * Prova relacionada com os espaços (vazio = null). Com `answersIncluded`, não há gabarito separado.
 * `edital`: o edital daquela prova (opcional; sem ele, vale o edital do concurso-alvo).
 */
export type StudyPaper = {
  id: string;
  year: number | null;
  /** Tipo/cor da prova ("Tipo 1", "Branca"). */
  variant: string | null;
  answersIncluded: boolean;
  createdAt: string;
  exam: StudyDocument | null;
  answerKey: StudyDocument | null;
  edital: StudyDocument | null;
};

/** Materiais de um concurso da carteira (GET /me/study-targets/{id}/materials). `editais`: os do concurso-alvo. */
export type StudyMaterials = {
  editais: StudyDocument[];
  papers: StudyPaper[];
};
