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
/** Aviso do sininho: concurso novo (`exam`) ou leitura do edital de um concurso da carteira (`studyTarget`). */
export type AppNotification = {
  id: string;
  createdAt: string;
  read: boolean;
} & (
  | { type: "NEW_EXAM"; exam: ExamSummary; studyTarget: null }
  | { type: "EDITAL_READ" | "EDITAL_FAILED"; exam: null; studyTarget: { id: string; title: string } }
);

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
  /** Leitura do edital pela IA (só no edital do concurso; null se nunca foi pedida). */
  reading: EditalReading | null;
  /** Cargo escolhido entre os da leitura, como o edital escreve: base do conteúdo programático. */
  position: { name: string; specialty: string | null } | null;
  /** Leitura do conteúdo programático do cargo escolhido (null se nenhum cargo ou não pedida). */
  syllabus: SyllabusReading | null;
};

/**
 * Leitura do edital: `PENDING` (na fila ou lendo) → `DONE` com a visão geral, ou `FAILED` com o motivo
 * (`retryable`: pedir de novo pode dar certo).
 */
export type EditalReading = {
  status: "PENDING" | "DONE" | "FAILED";
  overview: EditalOverview | null;
  failureReason: string | null;
  retryable: boolean;
  /** Feita com um prompt mais antigo que o atual: pedir de novo lê com o novo. */
  outdated: boolean;
};

/** Leitura do conteúdo programático do cargo: mesmos estados da leitura do edital. */
export type SyllabusReading = {
  status: "PENDING" | "DONE" | "FAILED";
  syllabus: EditalSyllabus | null;
  failureReason: string | null;
  retryable: boolean;
  outdated: boolean;
};

/** Disciplinas e tópicos do cargo. `positionFound` false: o edital não traz o conteúdo do cargo. */
export type EditalSyllabus = {
  positionFound: boolean;
  subjects: SyllabusSubject[];
};

/** `group`: bloco do edital ("Conhecimentos Específicos"), quando agrupa as disciplinas. */
export type SyllabusSubject = {
  name: string;
  group: string | null;
  topics: string[];
  page: number | null;
};

/** O que a IA leu do edital: sugestão para conferir e aplicar. Ausente no edital = null. */
export type EditalOverview = {
  organ: string | null;
  board: string | null;
  year: number | null;
  registrationStart: string | null;
  registrationEnd: string | null;
  examDate: string | null;
  positions: EditalPosition[];
  /** Quadro de provas (leitura v2 em diante; vazio na v1). */
  exams: EditalExam[];
};

/** Uma prova objetiva do quadro, comum a vários cargos. `essay`: tem discursiva ou redação. */
export type EditalExam = {
  id: number;
  appliesTo: string;
  answerFormat: "MULTIPLE_CHOICE" | "TRUE_FALSE" | null;
  totalQuestions: number | null;
  sections: { block: string | null; subject: string; questions: number | null; weight: number | null }[];
  essay: boolean;
};

/** `openings` e `salary` como o edital escreve ("3 + CR", "R$ 8.529,67"). */
export type EditalPosition = {
  name: string;
  specialty: string | null;
  educationLevel: EducationLevel | null;
  openings: string | null;
  salary: string | null;
  page: number | null;
  /** A prova (em `exams`) que vale para o cargo. */
  examId: number | null;
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
  /** Leitura das questões pela IA (null se nunca foi pedida, ou se o PDF da prova ou do gabarito mudou). */
  reading: PaperReading | null;
};

/** Leitura das questões da prova: mesmos estados da leitura do edital, com o resumo quando pronta. */
export type PaperReading = {
  status: "PENDING" | "DONE" | "FAILED";
  summary: {
    questions: number;
    annulled: number;
    /** Sem resposta no gabarito (fora anuladas e discursivas). */
    withoutAnswer: number;
    answerKeyFound: boolean;
    variant: string | null;
    bySubject: { subject: string; questions: number }[];
  } | null;
  failureReason: string | null;
  retryable: boolean;
  outdated: boolean;
};

/** Materiais de um concurso da carteira (GET /me/study-targets/{id}/materials). `editais`: os do concurso-alvo. */
export type StudyMaterials = {
  editais: StudyDocument[];
  papers: StudyPaper[];
};

/** Simulado (GET /me/simulations/{id}). `answer`, `annulled`, `correct` e `result` só depois de terminar. */
export type Simulation = {
  id: string;
  studyTargetId: string;
  title: string;
  startedAt: string;
  /** Sem limite de tempo: null. */
  deadline: string | null;
  finishedAt: string | null;
  finished: boolean;
  questions: SimulationQuestion[];
  result: SimulationResult | null;
};

export type SimulationQuestion = {
  id: string;
  number: number;
  subject: string | null;
  kind: "MULTIPLE_CHOICE" | "TRUE_FALSE";
  /** Texto-base compartilhado com outras questões. */
  baseText: string | null;
  statement: string;
  options: { letter: string; text: string }[];
  hasImage: boolean;
  page: number | null;
  /** Resposta marcada (letra, ou C/E). */
  chosen: string | null;
  answer: string | null;
  annulled: boolean | null;
  /** null: em branco ou sem resposta no gabarito. */
  correct: boolean | null;
  /** Tópico do conteúdo do cargo (depois de entregar, se a prova foi ligada aos tópicos). */
  topic: TopicRef | null;
  /** Inédita, gerada por IA para o tópico (não é de prova real). */
  generated: boolean;
  /** Comentário da resposta (questão gerada, depois de entregar). */
  explanation: string | null;
};

export type TopicRef = { subject: string; topic: string };

/** Anulada conta como certa. `net`: certas menos erradas, só em prova de certo/errado. */
export type SimulationResult = {
  total: number;
  correct: number;
  wrong: number;
  blank: number;
  annulled: number;
  unscored: number;
  net: number | null;
  bySubject: { subject: string; total: number; correct: number; wrong: number; blank: number }[];
  /** Onde mais errou primeiro. Vazio se a prova não foi ligada aos tópicos. */
  byTopic: { topic: TopicRef; total: number; correct: number; wrong: number; blank: number }[];
};

export type SimulationSummary = {
  id: string;
  title: string;
  startedAt: string;
  finishedAt: string | null;
  finished: boolean;
  answered: number;
  total: number;
  correct: number | null;
};

/** Questões das provas relacionadas ligadas aos tópicos do conteúdo do cargo (GET /me/study-targets/{id}/topics). */
export type TopicsOverview = {
  /** Há conteúdo do cargo lido para ligar. */
  syllabusReady: boolean;
  papers: PaperTopics[];
  /** Questões por tópico (ordinais a partir de 0, na ordem do conteúdo). */
  frequency: TopicFrequency[];
};

/** `outdated`: feita com outro prompt, outra leitura da prova ou outro conteúdo (ligar de novo atualiza). */
export type PaperTopics = {
  paperId: string;
  status: "PENDING" | "DONE" | "FAILED";
  failureReason: string | null;
  retryable: boolean;
  outdated: boolean;
};

export type TopicFrequency = { subjectOrdinal: number; topicOrdinal: number; questions: number };

/** Plano de estudos de um concurso (GET /me/study-targets/{id}/plan; 204 = sem plano). */
export type StudyPlan = {
  id: string;
  studyTargetId: string;
  /** A data da prova (o plano vai até a véspera). */
  endDate: string;
  /** Minutos por dia da semana, segunda primeiro. */
  dailyMinutes: number[];
  updatedAt: string;
  subjects: { subject: string; minutes: number; doneMinutes: number }[];
  sessions: PlanSession[];
};

export type PlanSessionKind = "STUDY" | "REVIEW" | "SIMULATION";

export type PlanSession = {
  id: string;
  date: string;
  kind: PlanSessionKind;
  /** null na revisão da semana e no simulado. */
  subject: string | null;
  topics: string[];
  minutes: number;
  done: boolean;
  /** Questões das provas relacionadas dos tópicos da sessão (só no plano; a agenda não traz). */
  practice?: number;
};

/** Sessão na agenda de todos os concursos. */
export type AgendaSession = PlanSession & { studyTargetId: string; targetTitle: string };

/** Um tópico do conteúdo (ordinais a partir de 0): questões para praticar (reais e geradas) e a geração. */
export type TopicPractice = {
  subjectOrdinal: number;
  topicOrdinal: number;
  practice: number;
  generation: GenerationState | null;
};

/** `outdated`: gerada com um prompt mais antigo. */
export type GenerationState = {
  status: "PENDING" | "DONE" | "FAILED";
  failureReason: string | null;
  retryable: boolean;
  outdated: boolean;
};
