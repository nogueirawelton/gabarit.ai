import "@fontsource-variable/inter";
import "./index.css";

import { QueryClientProvider } from "@tanstack/react-query";
import { lazy, StrictMode, Suspense } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oidc-context";
import { BrowserRouter } from "react-router";

import { App } from "./App";
import { oidcConfig } from "./auth/oidc";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { queryClient } from "./data/queryClient";

// só em dev: import dinâmico atrás de import.meta.env.DEV não entra no bundle de produção
const QueryDevtools = import.meta.env.DEV
  ? lazy(() =>
      import("@tanstack/react-query-devtools").then((m) => ({ default: m.ReactQueryDevtools })),
    )
  : null;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary>
      <AuthProvider {...oidcConfig}>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
          {QueryDevtools && (
            <Suspense>
              <QueryDevtools buttonPosition="bottom-left" />
            </Suspense>
          )}
        </QueryClientProvider>
      </AuthProvider>
    </ErrorBoundary>
  </StrictMode>,
);
