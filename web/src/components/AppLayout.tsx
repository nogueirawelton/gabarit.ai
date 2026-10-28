import { Outlet } from "react-router";

import { AppHeader } from "./AppHeader";

/**
 * Moldura da área logada: cabeçalho e dock montados uma vez só, as páginas trocam por baixo.
 * Assim a navegação não remonta o menu (o destaque do dock desliza e o cabeçalho não pisca).
 */
export function AppLayout() {
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
