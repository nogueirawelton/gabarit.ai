import { ArrowRight, MapPin, Search, Sparkles } from "lucide-react";
import { useAuth } from "react-oidc-context";
import { Link } from "react-router";

import type { Profile } from "../api";
import { displayPositionName } from "../brazil";
import { InterestsSkeleton, Loading } from "../components/Skeleton";
import { useProfile } from "../data/profile";

/** Área logada. Por enquanto: saudação e interesses; os concursos entram com a API de concursos (Fase 3). */
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

        <section className="mt-6 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900">
          <span className="mx-auto grid size-12 place-items-center rounded-xl bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
            <Search className="size-6" aria-hidden="true" />
          </span>
          <h2 className="mt-4 text-lg font-semibold">Concursos abertos para o seu cargo</h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-slate-600 dark:text-slate-400">
            Em breve você vai poder buscar e acompanhar os concursos abertos no Brasil inteiro por aqui.
          </p>
        </section>
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

  const { positions, ufs }: Profile = query.data;
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
      <p className="mt-4 inline-flex items-center gap-1.5 text-sm text-slate-500 dark:text-slate-400">
        <MapPin className="size-4" aria-hidden="true" />
        {ufs.length === 0 ? "Brasil inteiro" : ufs.join(", ")}
      </p>
    </section>
  );
}
