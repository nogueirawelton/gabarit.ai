import { type KeyboardEvent, useEffect, useRef, useState } from "react";
import { useLocation } from "react-router";

/**
 * Menu que abre por um botão (padrão "menu button" do WAI-ARIA): ao abrir, foca o primeiro item;
 * fecha com Esc (foco volta ao botão), Tab, clique fora ou ao navegar. Setas andam entre os itens
 * (`role="menuitem"`).
 */
export function useMenu() {
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  // navegou (inclusive pelo próprio menu): fecha
  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) {
      return;
    }
    menu.current?.querySelector<HTMLElement>("[role=menuitem]")?.focus();
    function onPointerDown(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  function onMenuKeyDown(event: KeyboardEvent) {
    const items = [...(menu.current?.querySelectorAll<HTMLElement>("[role=menuitem]") ?? [])];
    const index = items.indexOf(document.activeElement as HTMLElement);
    if (event.key === "Escape") {
      setOpen(false);
      trigger.current?.focus();
    } else if (event.key === "Tab") {
      setOpen(false);
    } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const step = event.key === "ArrowDown" ? 1 : -1;
      items[(index + step + items.length) % items.length]?.focus();
    }
  }

  return { open, setOpen, root, trigger, menu, onMenuKeyDown };
}
