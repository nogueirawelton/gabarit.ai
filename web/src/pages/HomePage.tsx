import { ArrowRight, GraduationCap, MapPin, Search, SearchX, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { Link, useSearchParams } from "react-router";

import type { Profile } from "../api";
import { displayPositionName, EDUCATION_LEVELS } from "../brazil";
import { ExamCard } from "../components/ExamCard";
import { ExamListSkeleton, InterestsSkeleton, Loading } from "../components/Skeleton";
import { type ExamScope, useExamList } from "../data/exams";
import { useProfile } from "../data/profile";

/** Área logada: saudação, interesses do perfil e os concursos (para você ou todos). */
export function HomePage() {
  const auth = useAuth();
  const firstName = auth.user?.profile.given_name ?? "";
  const profile = useProfile();

  return (
    <>
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{firstName ? `Olá, ${firstName}!` : "Olá!"}</h1>
        <p className="mt-2 text-slate-600 dark:text-slate-400">Que bom ver você por aqui.</p>

        <Interests query={profile} />

        <Exams profile={profile} />
      </div>
    </>
  );
}

function Interests({ query }: { query: ReturnType<typeof useProfile> }) {
  // skeleton só no primeiro carregamento; depois, o cache aparece na hora e atualiza por trás
  if (query.isPending) {
    return (
      <Loading>
        <InterestsSkeleton />
      </Loading>
    );
  }
  if (query.isError) {
    return (
      <p className="mt-10 text-sm text-red-700 dark:text-red-300">
        Não foi possível carregar seus interesses agora.
      </p>
    );
  }

  const { positions, ufs, educationLevels }: Profile = query.data;
  if (positions.length === 0) {
    return (
      <section className="mt-10 flex flex-col items-start gap-4 rounded-2xl bg-gradient-to-br from-indigo-600 to-violet-600 p-6 text-white sm:flex-row sm:items-center sm:justify-between sm:p-8">
        <div className="flex items-start gap-4">
          <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-white/15">
            <Sparkles className="size-5" aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-lg font-semibold">Diga quais cargos você quer prestar</h2>
            <p className="mt-1 text-sm text-indigo-100">
              Assim a gente mostra primeiro os concursos que importam para você.
            </p>
          </div>
        </div>
        <Link
          to="/perfil"
          className="inline-flex h-10 shrink-0 items-center gap-2 rounded-[10px] bg-white px-4 text-sm font-semibold text-indigo-700 transition hover:bg-indigo-50"
        >
          Configurar perfil
          <ArrowRight className="size-4" aria-hidden="true" />
        </Link>
      </section>
    );
  }

  return (
    <section className="mt-10 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-lg font-semibold">Seus interesses</h2>
        <Link to="/perfil" className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400">
          Editar
        </Link>
      </div>
      <ul className="mt-4 flex flex-wrap gap-2">
        {positions.map((p) => (
          <li
            key={p.id}
            className="rounded-full bg-indigo-50 px-3 py-1 text-sm font-medium text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300"
          >
            {displayPositionName(p.name)}
          </li>
        ))}
      </ul>
      <div className="mt-4 flex flex-wrap gap-x-5 gap-y-1.5 text-sm text-slate-500 dark:text-slate-400">
        <p className="inline-flex items-center gap-1.5">
          <MapPin className="size-4" aria-hidden="true" />
          {ufs.length === 0 ? "Brasil inteiro" : ufs.join(", ")}
        </p>
        <p className="inline-flex items-center gap-1.5">
          <GraduationCap className="size-4" aria-hidden="true" />
          {educationLevels.length === 0
            ? "Qualquer escolaridade"
            : EDUCATION_LEVELS.filter((level) => educationLevels.includes(level.value))
                .map((level) => level.label)
                .join(", ")}
        </p>
      </div>
    </section>
  );
}

/** Texto que só vale depois que a pessoa para de digitar: não busca a cada letra. */
function useDebounced(value: string, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

const SCOPES: { value: ExamScope; label: string }[] = [
  { value: "mine", label: "Para você" },
  { value: "all", label: "Todos" },
];

/**
 * Concursos com inscrição aberta, perto de abrir ou sem data, os que terminam antes primeiro.
 * "Para você": cargos e estados do perfil. Quem ainda não escolheu cargos começa em "Todos".
 */
function Exams({ profile }: { profile: ReturnType<typeof useProfile> }) {
  // busca e escopo na URL (?q=...&ver=todos): voltar do detalhe mantém a tela, e o link compartilha
  const [params, setParams] = useSearchParams();
  const chosenScope: ExamScope | null =
    params.get("ver") === "todos" ? "all" : params.get("ver") === "meus" ? "mine" : null;
  const [query, setQuery] = useState(() => params.get("q") ?? "");
  const q = useDebounced(query.trim());

  useEffect(() => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (q) {
          next.set("q", q);
        } else {
          next.delete("q");
        }
        return next;
      },
      { replace: true },
    );
  }, [q, setParams]);

  function setChosenScope(scope: ExamScope) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.set("ver", scope === "all" ? "todos" : "meus");
        return next;
      },
      { replace: true },
    );
  }
  const hasPositions = (profile.data?.positions.length ?? 0) > 0;
  // o padrão depende do perfil: espera ele chegar (senão busca "todos" e logo troca, piscando a lista)
  const scope: ExamScope = chosenScope ?? (hasPositions ? "mine" : "all");
  const ready = chosenScope !== null || !profile.isPending;
  const list = useExamList(scope, q, ready);

  const exams = list.data?.pages.flatMap((page) => page.items) ?? [];
  const total = list.data?.pages[0]?.totalElements ?? 0;

  return (
    <section className="mt-10" aria-labelledby="exams-title">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="exams-title" className="text-xl font-semibold">
            Concursos
          </h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Inscrições abertas e próximas, as que terminam antes primeiro.
          </p>
        </div>
        <div
          role="group"
          aria-label="Quais concursos mostrar"
          className="inline-flex rounded-xl border border-slate-200 bg-white p-1 dark:border-slate-800 dark:bg-slate-900"
        >
          {SCOPES.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={scope === option.value}
              onClick={() => setChosenScope(option.value)}
              className={`h-8 rounded-lg px-3.5 text-sm font-semibold transition ${
                scope === option.value
                  ? "bg-indigo-600 text-white dark:bg-indigo-500"
                  : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="relative mt-4">
        <Search
          className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Buscar pelo nome (ex.: prefeitura, tribunal, Campinas)"
          aria-label="Buscar concurso pelo nome"
          maxLength={100}
          className="h-11 w-full rounded-[10px] border border-slate-200 bg-white pr-3 pl-10 text-[15px] outline-none transition placeholder:text-slate-400 focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950"
        />
      </div>

      {!ready || list.isPending ? (
        <ExamListSkeleton />
      ) : list.isError ? (
        <p className="mt-6 text-sm text-red-700 dark:text-red-300">
          Não foi possível carregar os concursos agora. Recarregue a página.
        </p>
      ) : exams.length === 0 ? (
        <EmptyExams scope={scope} q={q} hasPositions={hasPositions} onShowAll={() => setChosenScope("all")} />
      ) : (
        <>
          <p className="mt-4 text-sm text-slate-500 dark:text-slate-400" role="status">
            {total === 1 ? "1 concurso" : `${total.toLocaleString("pt-BR")} concursos`}
          </p>
          {/* buscando outra coisa: a lista anterior fica, mais clara, até a nova chegar */}
          <ul
            className={`mt-3 grid gap-4 transition-opacity sm:grid-cols-2 ${list.isPlaceholderData ? "opacity-60" : ""}`}
          >
            {exams.map((exam) => (
              <li key={exam.id}>
                <ExamCard exam={exam} />
              </li>
            ))}
          </ul>
          {list.hasNextPage && (
            <div className="mt-6 flex justify-center">
              <button
                type="button"
                onClick={() => void list.fetchNextPage()}
                disabled={list.isFetchingNextPage}
                className="inline-flex h-10 items-center rounded-[10px] border border-slate-200 bg-white px-5 text-sm font-semibold text-slate-800 transition hover:border-slate-400 disabled:cursor-progress disabled:opacity-60 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600"
              >
                {list.isFetchingNextPage ? "Carregando…" : "Carregar mais"}
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}

function EmptyExams({
  scope,
  q,
  hasPositions,
  onShowAll,
}: {
  scope: ExamScope;
  q: string;
  hasPositions: boolean;
  onShowAll: () => void;
}) {
  let message: string;
  let action = null;
  if (q) {
    message = `Nenhum concurso encontrado para "${q}".`;
  } else if (scope === "mine" && !hasPositions) {
    message = "Escolha seus cargos no perfil para ver os concursos feitos para você.";
    action = (
      <Link to="/perfil" className="font-semibold text-indigo-600 hover:underline dark:text-indigo-400">
        Escolher cargos
      </Link>
    );
  } else if (scope === "mine") {
    message = "Nenhum concurso aberto para seus cargos, estados e escolaridade agora. A lista é atualizada duas vezes por dia.";
    action = (
      <button
        type="button"
        onClick={onShowAll}
        className="font-semibold text-indigo-600 hover:underline dark:text-indigo-400"
      >
        Ver todos os concursos
      </button>
    );
  } else {
    message = "Nenhum concurso com inscrições abertas no momento.";
  }

  return (
    <div className="mt-6 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
      <span className="mx-auto grid size-12 place-items-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
        <SearchX className="size-6" aria-hidden="true" />
      </span>
      <p className="mx-auto mt-4 max-w-md text-sm text-slate-600 dark:text-slate-400">{message}</p>
      {action && <p className="mt-3 text-sm">{action}</p>}
    </div>
  );
}
