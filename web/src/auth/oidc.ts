import { ErrorResponse, WebStorageStateStore } from "oidc-client-ts";
import type { AuthProviderProps } from "react-oidc-context";

import { config } from "../config";

/**
 * Login pelo Keycloak com Authorization Code + PKCE (cliente público, sem segredo no front).
 * A senha só é digitada na página do Keycloak; o app recebe os tokens no retorno.
 */
export const oidcConfig: AuthProviderProps = {
  authority: `${config.keycloakUrl}/realms/${config.keycloakRealm}`,
  client_id: config.keycloakClientId,
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/entrar`,
  response_type: "code",
  scope: "openid profile email",
  // renova o access token antes de expirar, usando o refresh token
  automaticSilentRenew: true,
  // tokens só nesta aba: fechou, saiu (o "manter conectado" do Keycloak mantém a sessão lá)
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // tira ?code=&state= da URL depois do login (o resto da busca, como ?passo=, fica)
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, withoutAuthParams(window.location.pathname, window.location.search));
  },
};

// parâmetros que o Keycloak põe na volta do login (com sucesso ou erro)
const AUTH_PARAMS = ["code", "state", "session_state", "iss", "error", "error_description", "error_uri"];

/** Caminho + busca sem os parâmetros da volta do login: para onde a pessoa queria ir. */
export function withoutAuthParams(pathname: string, search: string) {
  const params = new URLSearchParams(search);
  AUTH_PARAMS.forEach((param) => params.delete(param));
  const rest = params.toString();
  return rest ? `${pathname}?${rest}` : pathname;
}

/** URL de volta do login: o próprio caminho no app (o realm aceita qualquer caminho do web). */
export function returnUrl(path: string) {
  return `${window.location.origin}${path}`;
}

/**
 * A tentativa de entrar sem tela (`prompt=none`) voltou sem sessão no Keycloak: não é erro, só
 * precisa fazer login.
 */
export function isLoginRequired(error: unknown) {
  const inner = (error as { innerError?: unknown } | undefined)?.innerError ?? error;
  return inner instanceof ErrorResponse && inner.error === "login_required";
}
