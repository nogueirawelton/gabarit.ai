import { useAuth } from "react-oidc-context";

/**
 * Usuário logado para as queries. O `sub` entra na chave do cache (dados de um usuário nunca
 * aparecem para outro); o token só é usado na chamada (ele muda a cada renovação).
 */
export function useSession() {
  const auth = useAuth();
  return {
    userId: auth.user?.profile.sub,
    accessToken: auth.user?.access_token,
  };
}

export function requireToken(accessToken: string | undefined): string {
  if (!accessToken) {
    throw new Error("sem sessão");
  }
  return accessToken;
}
