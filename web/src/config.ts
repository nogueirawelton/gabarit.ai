/**
 * Configuração de runtime, lida de window.__GABARIT_CONFIG__ (public/config.js).
 * Fica fora do bundle: a mesma imagem serve qualquer ambiente, só mudando as variáveis do container.
 */
type RuntimeConfig = {
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
  apiUrl: string;
};

declare global {
  interface Window {
    __GABARIT_CONFIG__?: Partial<RuntimeConfig>;
  }
}

const runtime = window.__GABARIT_CONFIG__ ?? {};

export const config: RuntimeConfig = {
  keycloakUrl: runtime.keycloakUrl ?? "http://localhost:8180",
  keycloakRealm: runtime.keycloakRealm ?? "gabaritai",
  keycloakClientId: runtime.keycloakClientId ?? "web",
  apiUrl: runtime.apiUrl ?? "http://localhost:8080",
};
