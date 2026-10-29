import { useEffect } from "react";
import { Outlet, useLocation, useNavigationType } from "react-router";

import { AppHeader } from "./AppHeader";

/**
 * Moldura da área logada: cabeçalho e dock montados uma vez só, as páginas trocam por baixo.
 * Assim a navegação não remonta o menu (o destaque do dock desliza e o cabeçalho não pisca).
 */
export function AppLayout() {
  const { pathname } = useLocation();
  const navigationType = useNavigationType();

  // página nova começa no topo; no voltar/avançar (POP) o navegador restaura a rolagem
  useEffect(() => {
    if (navigationType !== "POP") {
      window.scrollTo(0, 0);
    }
  }, [pathname, navigationType]);

  return (
    <div className="min-h-svh">
      <AppHeader />
      {/* container único da área logada: mesma largura e espaçamento em todas as páginas */}
      <main className="mx-auto max-w-6xl px-4 pt-10 pb-dock sm:pt-12 sm:pb-12">
        <Outlet />
      </main>
    </div>
  );
}
