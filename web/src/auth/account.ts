import { config } from "../config";

/**
 * API de conta do Keycloak (o próprio usuário edita os dados dele). Usa o mesmo access token do
 * app: ele já vem com `aud: account` e a role `manage-account` (padrão do realm).
 * Senha não passa por aqui: trocar senha é tela do Keycloak (kc_action=UPDATE_PASSWORD).
 */
export type KeycloakAccount = {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  emailVerified: boolean;
  [field: string]: unknown;
};

const accountUrl = () => `${config.keycloakUrl}/realms/${config.keycloakRealm}/account`;

export async function getAccount(accessToken: string): Promise<KeycloakAccount> {
  const response = await fetch(accountUrl(), {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Keycloak respondeu ${response.status}`);
  }
  return (await response.json()) as KeycloakAccount;
}

/** Atualiza nome e sobrenome. O Keycloak espera a conta inteira (o resto vai como estava). */
export async function updateName(
  accessToken: string,
  account: KeycloakAccount,
  firstName: string,
  lastName: string,
): Promise<void> {
  const response = await fetch(accountUrl(), {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ ...account, firstName, lastName }),
  });
  if (!response.ok) {
    throw new Error(`Keycloak respondeu ${response.status}`);
  }
}
