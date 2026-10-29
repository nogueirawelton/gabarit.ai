import { useQueryClient } from "@tanstack/react-query";
import { BookOpen, ChevronDown, House, LogOut, UserRound, type LucideIcon } from "lucide-react";
import { useAuth } from "react-oidc-context";
import { Link, NavLink, useLocation } from "react-router";

import { Avatar } from "./Avatar";
import { NotificationBell } from "./NotificationBell";
import { Logo } from "./Logo";
import { useMenu } from "./useMenu";

const NAV: { to: string; label: string; icon: LucideIcon }[] = [
  { to: "/", label: "Início", icon: House },
  { to: "/estudos", label: "Estudos", icon: BookOpen },
];

/** Item ativo também nas subpáginas (/estudos/5 acende "Estudos"); o início só na própria raiz. */
function isActive(to: string, pathname: string) {
  return to === "/" ? pathname === "/" : pathname === to || pathname.startsWith(`${to}/`);
}

/**
 * Navegação da área logada. Desktop: menu no cabeçalho. Celular: dock flutuante embaixo (as
 * páginas reservam espaço para ele com `pb-dock`). Perfil e Sair: menu do usuário, nos dois.
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
  return (
    <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/80 backdrop-blur dark:border-slate-800 dark:bg-slate-950/80">
      <div className="mx-auto flex h-18 max-w-6xl items-center justify-between gap-3 px-4">
        <div className="flex items-center gap-8">
          <NavLink to="/" aria-label="gabarit.ai, página inicial">
            <Logo />
          </NavLink>
          <nav aria-label="Principal" className="hidden items-center gap-1 sm:flex">
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
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

        <div className="flex items-center gap-1 sm:gap-2">
          <UserMenu />
          <NotificationBell />
        </div>
      </div>
    </header>
  );
}

/** Menu do usuário: abre pelo avatar (comportamento em `useMenu`). */
function UserMenu() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const { pathname } = useLocation();
  const user = auth.user?.profile;
  const { open, setOpen, root, trigger, menu, onMenuKeyDown } = useMenu();

  function signOut() {
    // o próximo login no mesmo navegador não vê nada em cache deste usuário
    queryClient.clear();
    void auth.signoutRedirect();
  }

  return (
    <div ref={root} className="relative">
      <button
        ref={trigger}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Menu da conta"
        className={`flex items-center gap-3 rounded-xl p-1 transition hover:bg-slate-100 md:py-1 md:pr-2 md:pl-3 dark:hover:bg-slate-900 ${
          open ? "bg-slate-100 dark:bg-slate-900" : ""
        }`}
      >
        <span className="hidden text-right md:block">
          <span className="block text-sm leading-tight font-medium">{user?.name}</span>
          <span className="block text-xs text-slate-500 dark:text-slate-400">{user?.email}</span>
        </span>
        <Avatar />
        <ChevronDown
          className={`hidden size-4 text-slate-400 transition-transform md:block ${open ? "rotate-180" : ""}`}
          aria-hidden="true"
        />
      </button>

      {open && (
        <div
          ref={menu}
          role="menu"
          aria-label="Conta"
          onKeyDown={onMenuKeyDown}
          className="absolute right-0 mt-2 w-64 origin-top-right overflow-hidden rounded-xl border border-slate-200 bg-white p-1.5 shadow-lg shadow-slate-900/10 dark:border-slate-800 dark:bg-slate-900 dark:shadow-black/40"
        >
          {/* no celular o nome não aparece no cabeçalho: o menu mostra quem está logado */}
          <div className="flex items-center gap-3 px-2.5 py-2 md:hidden">
            <Avatar className="size-10 text-sm" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{user?.name}</p>
              <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user?.email}</p>
            </div>
          </div>
          <div className="my-1 h-px bg-slate-200 md:hidden dark:bg-slate-800" />

          <Link
            to="/perfil"
            role="menuitem"
            onClick={() => setOpen(false)}
            className={`${menuItem} ${pathname === "/perfil" ? "text-indigo-700 dark:text-indigo-300" : ""}`}
          >
            <UserRound className="size-4" aria-hidden="true" />
            Perfil
          </Link>
          <button type="button" role="menuitem" onClick={signOut} className={menuItem}>
            <LogOut className="size-4" aria-hidden="true" />
            Sair
          </button>
        </div>
      )}
    </div>
  );
}

const menuItem =
  "flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2 text-left text-sm font-medium text-slate-700 outline-none transition hover:bg-slate-100 focus-visible:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 dark:focus-visible:bg-slate-800";

/**
 * Dock do celular: pílula flutuante embaixo (acima da barra de gestos do iPhone). Um único
 * destaque cobre ícone + nome do item ativo e desliza entre os itens.
 */
function MobileDock() {
  const { pathname } = useLocation();
  const active = NAV.findIndex((item) => isActive(item.to, pathname));

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
              end={to === "/"}
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
