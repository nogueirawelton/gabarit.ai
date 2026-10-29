import { useState } from "react";
import { useAuth } from "react-oidc-context";

import { useProfile } from "../data/profile";

/**
 * Foto do usuário logado. Sem foto, ou se a URL falhar (expirada, MinIO fora), mostra as iniciais
 * do nome. Decorativa: o nome aparece ao lado.
 */
export function Avatar({ className = "size-9 text-sm" }: { className?: string }) {
  const auth = useAuth();
  const profile = useProfile();
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const user = auth.user?.profile;
  const url = profile.data?.avatarUrl;

  if (url && url !== failedUrl) {
    return (
      <img
        src={url}
        alt=""
        onError={() => setFailedUrl(url)}
        className={`shrink-0 rounded-full bg-slate-100 object-cover dark:bg-slate-800 ${className}`}
      />
    );
  }

  const initials = (user?.name ?? user?.email ?? "?")
    .split(/\s+/)
    .map((part) => part[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
  return (
    <span
      aria-hidden="true"
      className={`grid shrink-0 place-items-center rounded-full bg-indigo-100 font-semibold text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300 ${className}`}
    >
      {initials}
    </span>
  );
}
