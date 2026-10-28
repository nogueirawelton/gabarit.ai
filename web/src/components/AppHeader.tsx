import { useQueryClient } from "@tanstack/react-query";
import { House, LogOut, UserRound, type LucideIcon } from "lucide-react";
import { useAuth } from "react-oidc-context";
import { NavLink, useLocation } from "react-router";

import { Logo } from "./Logo";

const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: "/", label: "Início", icon: House },
  { to: "/perfil", label: "Perfil", icon: UserRound },
];

/**
 * Navegação da área logada. Desktop: menu no cabeçalho. Celular: dock flutuante embaixo (as
 * páginas reservam espaço para ele com `pb-dock`).
 */
export function AppHeader() {
  return (
    <>
      <Header />
      <MobileDock />
    </>
  );
}

function Header() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const profile = auth.user?.profile;
  const initials = (profile?.name ?? profile?.email ?? "?")
    .split(/\s+/)
    .map((part) => part[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-950/80">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4">
        <div className="flex items-center gap-8">
          <NavLink to="/" aria-label="gabarit.ai, página inicial">
            <Logo />
          </NavLink>
          <nav aria-label="Principal" className="hidden items-center gap-1 sm:flex">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end
                className={({ isActive }) =>
                  `rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                    isActive
                      ? "bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-100"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-1 sm:gap-3">
          <div className="hidden text-right md:block">
            <p className="text-sm leading-tight font-medium">{profile?.name}</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">{profile?.email}</p>
          </div>
          <span className="grid size-9 place-items-center rounded-full bg-indigo-100 text-sm font-semibold text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">
            {initials}
          </span>
          <button
            type="button"
            onClick={() => {
              // o próximo login no mesmo navegador não vê nada em cache deste usuário
              queryClient.clear();
              void auth.signoutRedirect();
            }}
            aria-label="Sair"
            className="inline-flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900 sm:px-3 dark:text-slate-400 dark:hover:bg-slate-900 dark:hover:text-slate-100"
          >
            <LogOut className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">Sair</span>
          </button>
        </div>
      </div>
    </header>
  );
}

/**
 * Dock do celular: pílula flutuante embaixo (acima da barra de gestos do iPhone). Um único
 * destaque cobre ícone + nome do item ativo e desliza entre os itens.
 */
function MobileDock() {
  const { pathname } = useLocation();
  const active = NAV.findIndex((item) => item.to === pathname);

  return (
    <nav
      aria-label="Principal"
      className="fixed inset-x-0 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-20 flex justify-center px-4 sm:hidden"
    >
      <ul
        className="relative grid w-full max-w-md rounded-2xl border border-slate-200/80 bg-white/85 p-1.5 shadow-lg shadow-slate-900/10 backdrop-blur-md dark:border-slate-800 dark:bg-slate-900/85 dark:shadow-black/40"
        style={{ gridTemplateColumns: `repeat(${NAV.length}, minmax(0, 1fr))` }}
      >
        {active >= 0 && (
          <li
            aria-hidden="true"
            className="absolute top-1.5 bottom-1.5 left-1.5 rounded-xl bg-indigo-50 motion-safe:transition-transform motion-safe:duration-300 motion-safe:ease-out dark:bg-indigo-500/15"
            // 1/N da área interna (sem os 0,375rem de padding de cada lado); translateX em % da própria largura
            style={{ width: `calc((100% - 0.75rem) / ${NAV.length})`, transform: `translateX(${active * 100}%)` }}
          />
        )}
        {NAV.map(({ to, label, icon: Icon }) => (
          <li key={to} className="relative">
            <NavLink
              to={to}
              end
              className={({ isActive }) =>
                `flex h-14 flex-col items-center justify-center gap-0.5 rounded-xl text-[11px] font-semibold tracking-wide transition-colors ${
                  isActive
                    ? "text-indigo-700 dark:text-indigo-300"
                    : "text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className="size-[22px]" strokeWidth={isActive ? 2.3 : 1.8} aria-hidden="true" />
                  {label}
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
