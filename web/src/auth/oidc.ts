import { WebStorageStateStore } from "oidc-client-ts";
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
  // tira ?code=&state= da URL depois do login
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
