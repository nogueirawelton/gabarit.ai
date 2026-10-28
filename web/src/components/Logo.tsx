import { useId } from "react";

type LogoProps = {
  /** "light" para fundo escuro (painel da marca) */
  tone?: "default" | "light";
  className?: string;
};

export function LogoMark({ className = "size-8" }: { className?: string }) {
  // id único por instância: com id fixo, um logo escondido (display: none) "apaga" o gradiente dos outros
  const gradientId = useId();
  return (
    <svg viewBox="0 0 32 32" fill="none" aria-hidden="true" className={className}>
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          <stop stopColor="#6366F1" />
          <stop offset="1" stopColor="#8B5CF6" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill={`url(#${gradientId})`} />
      <path
        d="M9.5 16.5l4.2 4.2L22.8 11.6"
        stroke="#fff"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function Logo({ tone = "default", className = "" }: LogoProps) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <LogoMark />
      <span className="text-xl font-bold tracking-tight">
        gabarit
        <span className={tone === "light" ? "text-violet-300" : "text-indigo-600 dark:text-indigo-400"}>
          .ai
        </span>
      </span>
    </span>
  );
}
