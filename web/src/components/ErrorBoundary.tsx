import { Component, type ErrorInfo, type ReactNode } from "react";

import { LogoMark } from "./Logo";

type State = { error: Error | null };

/** Erro de renderização vira uma tela com a mensagem, em vez de página em branco. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("erro de renderização", error, info.componentStack);
  }

  render() {
    if (!this.state.error) {
      return this.props.children;
    }
    return (
      <div className="grid min-h-svh place-items-center px-4">
        <div className="w-full max-w-sm text-center">
          <LogoMark className="mx-auto size-10" />
          <h1 className="mt-6 text-xl font-bold tracking-tight">Algo deu errado</h1>
          <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
            Tente de novo. Se continuar, avise a gente com a mensagem abaixo.
          </p>
          <pre className="mt-4 overflow-x-auto rounded-lg bg-slate-100 p-3 text-left text-xs text-slate-700 dark:bg-slate-900 dark:text-slate-300">
            {this.state.error.message}
          </pre>
          <a
            href="/"
            className="mt-6 inline-flex h-10 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white hover:bg-indigo-700"
          >
            Voltar ao início
          </a>
        </div>
      </div>
    );
  }
}
