import { ArrowRight, Check, ShieldCheck } from "lucide-react";
import { useAuth } from "react-oidc-context";
import { Navigate, useLocation } from "react-router";

import { isLoginRequired, returnUrl } from "../auth/oidc";
import { Logo } from "../components/Logo";
import { LoginSkeleton } from "../components/Skeleton";

const HIGHLIGHTS = [
  "Concursos abertos, atualizados todos os dias",
  "Filtre pelo seu cargo e pela sua região",
  "Plano de estudos para o seu cargo (em breve)",
];

/**
 * Entrada do app. Os botões só redirecionam: o formulário de login e o de cadastro são do
 * Keycloak (tema "gabarit", mesmo visual desta tela).
 */
export function LoginPage() {
  const auth = useAuth();
  const location = useLocation();
  const state = location.state as { error?: string; returnTo?: string } | null;
  // sem sessão no Keycloak (aba nova, tentativa sem tela) não é erro: é só entrar
  const error = (auth.error && !isLoginRequired(auth.error) ? auth.error.message : undefined) ?? state?.error;
  // depois do login, volta para a página que a pessoa abriu
  const returnTo = state?.returnTo ?? "/";

  if (auth.isAuthenticated) {
    return <Navigate to={returnTo} replace />;
  }

  const busy = Boolean(auth.activeNavigator);

  return (
    <div className="grid min-h-svh lg:grid-cols-2">
      <BrandPanel />

      <main className="flex flex-col items-center justify-start px-4 py-10 lg:justify-center">
        {auth.isLoading ? (
          <LoginSkeleton />
        ) : (
        <div className="w-full max-w-sm">
          <Logo className="mb-10 lg:hidden" />

          <h1 className="text-[26px] leading-tight font-bold tracking-tight">Bem-vindo ao gabarit.ai</h1>
          <p className="mt-2 text-slate-600 dark:text-slate-400">
            Entre para acompanhar os concursos abertos para o seu cargo.
          </p>

          {error && (
            <div
              role="alert"
              className="mt-6 rounded-[10px] border border-red-600/25 bg-red-50 px-3.5 py-3 text-sm text-red-700 dark:bg-red-400/10 dark:text-red-300"
            >
              <p>Não foi possível concluir o login. Tente entrar de novo.</p>
              <p className="mt-1 text-xs opacity-80">Detalhe: {error}</p>
            </div>
          )}

          <div className="mt-8 grid gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={() => void auth.signinRedirect({ redirect_uri: returnUrl(returnTo) })}
              className="group inline-flex h-11 items-center justify-center gap-2 rounded-[10px] bg-indigo-600 px-4 font-semibold text-white transition hover:bg-indigo-700 focus-visible:ring-4 focus-visible:ring-indigo-600/25 focus-visible:outline-none disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400"
            >
              Entrar
              <ArrowRight className="size-4 transition group-hover:translate-x-0.5" aria-hidden="true" />
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => void auth.signinRedirect({ prompt: "create", redirect_uri: returnUrl(returnTo) })}
              className="inline-flex h-11 items-center justify-center rounded-[10px] border border-slate-200 bg-white px-4 font-semibold text-slate-900 transition hover:border-slate-400 focus-visible:ring-4 focus-visible:ring-indigo-600/25 focus-visible:outline-none disabled:cursor-progress disabled:opacity-70 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100 dark:hover:border-slate-600"
            >
              Criar conta grátis
            </button>
          </div>

          <p className="mt-6 flex items-start gap-2 text-sm text-slate-500 dark:text-slate-400">
            <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            Você vai para a página segura de login do gabarit.ai. Sua senha nunca passa por este app.
          </p>
        </div>
        )}

        <footer className="mt-16 text-[13px] text-slate-500 lg:mt-0 lg:pt-24 dark:text-slate-500">© gabarit.ai</footer>
      </main>
    </div>
  );
}

function BrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-[radial-gradient(120%_90%_at_0%_0%,#4f46e5_0%,#3730a3_45%,#1e1b4b_100%)] px-14 py-12 text-indigo-50 lg:flex lg:flex-col lg:justify-between">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute right-[-20%] bottom-[-35%] size-130 rounded-full bg-[radial-gradient(circle,rgba(167,139,250,0.45)_0%,rgba(167,139,250,0)_70%)]"
      />

      <Logo tone="light" className="relative" />

      <div className="relative max-w-md">
        <h2 className="text-[34px] leading-[1.15] font-bold tracking-tight">
          Seu próximo concurso começa com um plano.
        </h2>
        <p className="mt-4 text-base text-indigo-100/80">
          Acompanhe os editais abertos no Brasil inteiro e estude com foco no seu cargo.
        </p>
        <ul className="mt-7 grid gap-3">
          {HIGHLIGHTS.map((item) => (
            <li key={item} className="flex items-center gap-2.5 text-indigo-50/95">
              <span className="grid size-5 place-items-center rounded-md bg-white/15">
                <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
              </span>
              {item}
            </li>
          ))}
        </ul>
      </div>

      <p className="relative text-[13px] text-indigo-100/60">
        Dados públicos de concursos, organizados para você estudar.
      </p>
    </aside>
  );
}
