import { useMutation } from "@tanstack/react-query";
import { useAuth } from "react-oidc-context";

import { getAccount, updateName } from "../auth/account";
import { requireToken } from "./session";

/**
 * Troca nome e sobrenome no Keycloak. Os dados da conta vêm do token (não ficam no cache do
 * React Query): no sucesso, renova o token para o nome novo aparecer no cabeçalho.
 */
export function useUpdateName() {
  const auth = useAuth();
  return useMutation({
    mutationFn: async ({ firstName, lastName }: { firstName: string; lastName: string }) => {
      const token = requireToken(auth.user?.access_token);
      const account = await getAccount(token);
      await updateName(token, account, firstName, lastName);
    },
    onSuccess: () => auth.signinSilent(),
  });
}
