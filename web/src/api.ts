import { config } from "./config";

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
  const response = await fetch(`${config.apiUrl}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${accessToken}`,
      ...(body !== undefined && { "Content-Type": "application/json" }),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new ApiError(response.status, problem?.detail);
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

/** Usuário logado, segundo o core (GET /me). */
export type Me = {
  id: string;
  email: string | null;
  name: string | null;
  givenName: string | null;
};

/** Cargo do catálogo (GET /catalog/positions). */
export type CatalogPosition = {
  id: number;
  slug: string;
  name: string;
};

/** Preferências de estudo (GET/PUT /me/profile). */
export type Profile = {
  positions: CatalogPosition[];
  ufs: string[];
};
