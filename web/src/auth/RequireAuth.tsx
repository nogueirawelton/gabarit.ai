import { type ReactNode, useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { Navigate, useLocation } from "react-router";

import { AppShellSkeleton } from "../components/Skeleton";
import { isLoginRequired, returnUrl, withoutAuthParams } from "./oidc";

/**
 * Só mostra o conteúdo com usuário logado. Sem usuário nesta aba (os tokens ficam por aba: link
 * aberto em aba nova chega sem eles), tenta entrar sem tela pela sessão do Keycloak, voltando para
 * o mesmo endereço; sem sessão lá, manda para /entrar, que depois do login volta para cá.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const location = useLocation();
  const here = withoutAuthParams(location.pathname, location.search);
  const trySilent = !auth.isLoading && !auth.activeNavigator && !auth.error && !auth.isAuthenticated;

  useEffect(() => {
    if (trySilent) {
      void auth.signinRedirect({ prompt: "none", redirect_uri: returnUrl(here) });
    }
    // uma vez por carregamento: a volta do Keycloak recarrega a página
  }, [trySilent]);

  // carregando a sessão, indo ao Keycloak ou voltando dele (?code=...)
  if (auth.isLoading || auth.activeNavigator || trySilent) {
    return <AppShellSkeleton />;
  }
  if (auth.error) {
    if (isLoginRequired(auth.error)) {
      return <Navigate to="/entrar" replace state={{ returnTo: here }} />;
    }
    // ex.: voltou do Keycloak mas o "state" não está nesta aba, ou o código expirou
    console.error("falha no login", auth.error);
    return <Navigate to="/entrar" replace state={{ error: auth.error.message, returnTo: here }} />;
  }
  return children;
}
