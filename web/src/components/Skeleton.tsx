import type { ReactNode } from "react";

import { REGIONS } from "../brazil";

/**
 * Bloco cinza no formato do conteúdo que vai chegar. Pulsa só se o sistema não pediu menos
 * animação (prefers-reduced-motion).
 */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden="true" className={`rounded-md bg-slate-200 motion-safe:animate-pulse dark:bg-slate-800 ${className}`} />;
}

/** Área que está carregando: anuncia para leitor de tela e esconde os blocos dele. */
export function Loading({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div role="status" aria-busy="true" className={className}>
      <span className="sr-only">Carregando…</span>
      {children}
    </div>
  );
}

/** Área logada inteira (sessão carregando ou voltando do Keycloak). */
export function AppShellSkeleton() {
  return (
    <Loading className="min-h-svh">
      <div className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-8">
            <Skeleton className="h-8 w-32 rounded-[9px]" />
            <div className="hidden gap-2 sm:flex">
              <Skeleton className="h-8 w-16 rounded-lg" />
              <Skeleton className="h-8 w-16 rounded-lg" />
            </div>
          </div>
          <Skeleton className="size-9 rounded-full" />
        </div>
      </div>
      <div className="mx-auto max-w-6xl px-4 pt-10 pb-dock sm:pt-12 sm:pb-12">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="mt-3 h-5 w-72 max-w-full" />
        <InterestsSkeleton />
        <Skeleton className="mt-6 h-48 rounded-2xl" />
      </div>
      {/* dock do celular */}
      <div className="fixed inset-x-0 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] flex justify-center px-4 sm:hidden">
        <div className="grid w-full max-w-md grid-cols-2 rounded-2xl border border-slate-200 bg-white p-1.5 dark:border-slate-800 dark:bg-slate-900">
          {[0, 1].map((i) => (
            <div key={i} className="flex h-14 flex-col items-center justify-center gap-1.5">
              <Skeleton className="size-5 rounded-md" />
              <Skeleton className="h-2 w-10" />
            </div>
          ))}
        </div>
      </div>
    </Loading>
  );
}

/** Cartão "Seus interesses" da home. */
export function InterestsSkeleton() {
  return (
    <div className="mt-10 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-center justify-between">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-4 w-12" />
      </div>
      <div className="mt-5 flex flex-wrap gap-2">
        {["w-44", "w-36", "w-28", "w-40"].map((w) => (
          <Skeleton key={w} className={`h-7 rounded-full ${w}`} />
        ))}
      </div>
      <Skeleton className="mt-5 h-4 w-32" />
    </div>
  );
}

/** Bloco "Preferências de estudo" do perfil: etiquetas, busca e estados por região. */
export function PreferencesSkeleton() {
  return (
    <Loading className="mt-6">
      <div className="flex items-center justify-between">
        <Skeleton className="h-5 w-36" />
        <Skeleton className="h-3 w-12" />
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        {["w-48", "w-40", "w-28"].map((w) => (
          <Skeleton key={w} className={`h-8 rounded-full ${w}`} />
        ))}
      </div>
      <Skeleton className="mt-3 h-11 rounded-[10px]" />

      <Skeleton className="mt-8 h-5 w-40" />
      <Skeleton className="mt-2 h-4 w-64 max-w-full" />
      <div className="mt-4 grid gap-4">
        {REGIONS.map((region) => (
          <div key={region.name} className="grid gap-2 sm:grid-cols-[120px_1fr] sm:items-center">
            <Skeleton className="h-4 w-20" />
            <div className="flex flex-wrap gap-2">
              {region.ufs.map((uf) => (
                <Skeleton key={uf} className="h-9 w-12 rounded-lg" />
              ))}
            </div>
          </div>
        ))}
      </div>
    </Loading>
  );
}

/** Lado direito da tela de entrada (o painel da marca não depende de nada e já aparece). */
export function LoginSkeleton() {
  return (
    <Loading className="w-full max-w-sm">
      <Skeleton className="h-8 w-64 max-w-full" />
      <Skeleton className="mt-3 h-5 w-full" />
      <Skeleton className="mt-2 h-5 w-40" />
      <Skeleton className="mt-8 h-11 rounded-[10px]" />
      <Skeleton className="mt-3 h-11 rounded-[10px]" />
    </Loading>
  );
}
