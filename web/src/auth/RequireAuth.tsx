import type { ReactNode } from "react";
import { useAuth } from "react-oidc-context";
import { Navigate } from "react-router";

import { AppShellSkeleton } from "../components/Skeleton";

/** Só mostra o conteúdo com usuário logado; senão manda para /entrar. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();

  // carregando a sessão ou voltando do Keycloak (?code=...)
  if (auth.isLoading || auth.activeNavigator) {
    return <AppShellSkeleton />;
  }
  if (auth.error) {
    // ex.: voltou do Keycloak mas o "state" não está nesta aba, ou o código expirou
    console.error("falha no login", auth.error);
    return <Navigate to="/entrar" replace state={{ error: auth.error.message }} />;
  }
  if (!auth.isAuthenticated) {
    return <Navigate to="/entrar" replace />;
  }
  return children;
}
