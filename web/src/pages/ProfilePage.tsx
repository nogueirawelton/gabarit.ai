import { BadgeCheck, Camera, Check, KeyRound, Pencil, Plus, Search, X } from "lucide-react";
import { type ChangeEvent, type FormEvent, type ReactNode, useMemo, useRef, useState } from "react";
import { useAuth } from "react-oidc-context";

import { ApiError, type CatalogPosition, type EducationLevel, type Profile } from "../api";
import { prepareAvatar } from "../avatar";
import { displayPositionName, EDUCATION_LEVELS, normalizeForSearch, REGIONS } from "../brazil";
import { Avatar } from "../components/Avatar";
import { PreferencesSkeleton } from "../components/Skeleton";
import { useUpdateName } from "../data/account";
import {
  useCatalog,
  useChangeEmailDigest,
  useProfile,
  useRemoveAvatar,
  useUpdateProfile,
  useUploadAvatar,
} from "../data/profile";

const MAX_POSITIONS = 30;

type Draft = { positionIds: string[]; ufs: string[]; educationLevels: EducationLevel[] };

type SaveStatus = { state: "idle" } | { state: "saving" } | { state: "saved" } | { state: "error"; message: string };

function toDraft(profile: Profile): Draft {
  return { positionIds: profile.positions.map((p) => p.id), ufs: profile.ufs, educationLevels: profile.educationLevels };
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError && error.detail ? error.detail : fallback;
}

export function ProfilePage() {
  const profile = useProfile();
  const catalog = useCatalog();
  const updateProfile = useUpdateProfile();
  // rascunho só existe enquanto há mudança não salva; sem ele, a tela mostra o que está no cache
  const [draft, setDraft] = useState<Draft | null>(null);
  const [justSaved, setJustSaved] = useState(false);

  const saved = profile.data ? toDraft(profile.data) : null;
  const current = draft ?? saved;
  const dirty = draft !== null && saved !== null && !sameDraft(draft, saved);

  const status: SaveStatus = updateProfile.isPending
    ? { state: "saving" }
    : updateProfile.isError
      ? { state: "error", message: errorMessage(updateProfile.error, "Não foi possível salvar agora.") }
      : justSaved
        ? { state: "saved" }
        : { state: "idle" };

  function update(next: Draft) {
    setDraft(next);
    setJustSaved(false);
    updateProfile.reset();
  }

  function save() {
    if (!draft) {
      return;
    }
    updateProfile.mutate(draft, {
      onSuccess: () => {
        setDraft(null);
        setJustSaved(true);
      },
    });
  }

  function discard() {
    setDraft(null);
    updateProfile.reset();
  }

  return (
    <>
      {/* espaço extra para a barra de salvar não cobrir o fim do formulário */}
      <div className="pb-24">
        <h1 className="text-3xl font-bold tracking-tight">Perfil</h1>
        <p className="mt-2 text-slate-600 dark:text-slate-400">Sua conta e o que você quer acompanhar.</p>

        <AccountSection />

        <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
          <h2 className="text-lg font-semibold">Preferências de estudo</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Usamos isso para mostrar e avisar sobre os concursos que importam para você.
          </p>

          {profile.isError || catalog.isError ? (
            <p className="mt-6 text-sm text-red-700 dark:text-red-300">
              Não foi possível carregar suas preferências. Recarregue a página.
            </p>
          ) : !catalog.data || !current ? (
            // só no primeiro carregamento: voltando para a tela, os dados já vêm do cache
            <PreferencesSkeleton />
          ) : (
            <>
              <PositionsPicker
                catalog={catalog.data}
                selected={current.positionIds}
                onChange={(positionIds) => update({ ...current, positionIds })}
              />
              <EducationPicker
                selected={current.educationLevels}
                onChange={(educationLevels) => update({ ...current, educationLevels })}
              />
              <UfPicker selected={current.ufs} onChange={(ufs) => update({ ...current, ufs })} />
            </>
          )}
        </section>

        <NotificationSettings />
      </div>

      <SaveBar dirty={dirty} status={status} onSave={save} onDiscard={discard} />
    </>
  );
}

function sameDraft(a: Draft, b: Draft) {
  const same = (x: (string | number)[], y: (string | number)[]) =>
    x.length === y.length && [...x].sort().join() === [...y].sort().join();
  return same(a.positionIds, b.positionIds) && same(a.ufs, b.ufs) && same(a.educationLevels, b.educationLevels);
}

/**
 * Dados da conta (do Keycloak). Nome: editado aqui mesmo, pela API de conta do Keycloak.
 * Senha: tela do Keycloak com o visual do app (a senha nunca passa pelo nosso código).
 */
function AccountSection() {
  const auth = useAuth();
  const profile = auth.user?.profile;
  const updateName = useUpdateName();
  const [editing, setEditing] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [nameSaved, setNameSaved] = useState(false);

  const nameStatus: SaveStatus = updateName.isPending
    ? { state: "saving" }
    : updateName.isError
      ? { state: "error", message: "Não foi possível salvar o nome agora." }
      : nameSaved
        ? { state: "saved" }
        : { state: "idle" };

  function startEditing() {
    setFirstName(profile?.given_name ?? "");
    setLastName(profile?.family_name ?? "");
    setNameSaved(false);
    updateName.reset();
    setEditing(true);
  }

  function saveName(event: FormEvent) {
    event.preventDefault();
    updateName.mutate(
      { firstName: firstName.trim(), lastName: lastName.trim() },
      {
        onSuccess: () => {
          setEditing(false);
          setNameSaved(true);
        },
        onError: (error) => console.error("falha ao salvar o nome", error),
      },
    );
  }

  function changePassword() {
    void auth.signinRedirect({
      redirect_uri: `${window.location.origin}/perfil`,
      extraQueryParams: { kc_action: "UPDATE_PASSWORD" },
    });
  }

  const canSaveName = firstName.trim() !== "" && lastName.trim() !== "" && nameStatus.state !== "saving";

  return (
    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="sr-only">Conta</h2>
      <IdentityHeader />

      {/* uma linha por item: rótulo | valor | ação. Todas no mesmo grid, para alinhar entre si */}
      <dl className="mt-6 divide-y divide-slate-200 border-t border-slate-200 dark:divide-slate-800 dark:border-slate-800">
        <AccountRow
          label="Nome"
          action={
            !editing && (
              <button type="button" onClick={startEditing} className={rowButton}>
                <Pencil className="size-4" aria-hidden="true" />
                Editar
              </button>
            )
          }
        >
          {editing ? (
            <form onSubmit={(e) => void saveName(e)} className="max-w-xl">
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="grid gap-1.5 text-sm">
                  <span className="text-slate-500 dark:text-slate-400">Primeiro nome</span>
                  <input
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    autoComplete="given-name"
                    autoFocus
                    required
                    className={inputClass}
                  />
                </label>
                <label className="grid gap-1.5 text-sm">
                  <span className="text-slate-500 dark:text-slate-400">Sobrenome</span>
                  <input
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    autoComplete="family-name"
                    required
                    className={inputClass}
                  />
                </label>
              </div>
              {nameStatus.state === "error" && (
                <p className="mt-2 text-sm text-red-700 dark:text-red-300">{nameStatus.message}</p>
              )}
              <div className="mt-3 flex gap-2">
                <button
                  type="submit"
                  disabled={!canSaveName}
                  className="inline-flex h-9 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-indigo-500 dark:hover:bg-indigo-400"
                >
                  {nameStatus.state === "saving" ? "Salvando…" : "Salvar"}
                </button>
                <button type="button" onClick={() => setEditing(false)} className={rowButton}>
                  Cancelar
                </button>
              </div>
            </form>
          ) : (
            <span className="flex flex-wrap items-center gap-2">
              {profile?.name ?? "—"}
              {nameStatus.state === "saved" && (
                <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 dark:text-emerald-400">
                  <Check className="size-3.5" aria-hidden="true" />
                  salvo
                </span>
              )}
            </span>
          )}
        </AccountRow>

        <AccountRow label="E-mail">
          <span className="flex flex-wrap items-center gap-2">
            <span className="break-all">{profile?.email ?? "—"}</span>
            {profile?.email_verified && (
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-400">
                <BadgeCheck className="size-3.5" aria-hidden="true" />
                confirmado
              </span>
            )}
          </span>
        </AccountRow>

        <AccountRow
          label="Senha"
          action={
            <button type="button" onClick={changePassword} className={rowButton}>
              <KeyRound className="size-4" aria-hidden="true" />
              Trocar senha
            </button>
          }
        >
          <span className="tracking-[0.2em] text-slate-500 dark:text-slate-400" aria-label="senha definida">
            ••••••••
          </span>
        </AccountRow>
      </dl>
    </section>
  );
}

/**
 * Topo do card da conta: foto, nome e e-mail. Clicar na foto (ou em "Trocar foto") abre o seletor;
 * o navegador recorta e reduz antes de enviar (`prepareAvatar`). A troca aparece na hora aqui e no
 * cabeçalho (mesmo cache do perfil).
 */
function IdentityHeader() {
  const auth = useAuth();
  const user = auth.user?.profile;
  const profile = useProfile();
  const upload = useUploadAvatar();
  const remove = useRemoveAvatar();
  const input = useRef<HTMLInputElement>(null);
  const [preparing, setPreparing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const uploading = preparing || upload.isPending;
  const busy = uploading || remove.isPending;
  const hasPhoto = Boolean(profile.data?.avatarUrl);

  async function onFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // limpa a seleção: escolher o mesmo arquivo de novo dispara o onChange outra vez
    event.target.value = "";
    if (!file) {
      return;
    }
    setError(null);
    setPreparing(true);
    let image: Blob;
    try {
      image = await prepareAvatar(file);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível abrir essa imagem.");
      return;
    } finally {
      setPreparing(false);
    }
    upload.mutate(image, {
      onError: (e) => setError(errorMessage(e, "Não foi possível enviar a foto agora.")),
    });
  }

  function removePhoto() {
    setError(null);
    remove.mutate(undefined, {
      onError: (e) => setError(errorMessage(e, "Não foi possível remover a foto agora.")),
    });
  }

  return (
    <div className="flex items-center gap-4 sm:gap-5">
      <button
        type="button"
        onClick={() => input.current?.click()}
        disabled={busy}
        aria-label={hasPhoto ? "Trocar foto de perfil" : "Enviar foto de perfil"}
        className="group relative shrink-0 rounded-full outline-none focus-visible:ring-4 focus-visible:ring-indigo-600/30 disabled:cursor-progress"
      >
        <Avatar className={`size-16 text-xl sm:size-20 sm:text-2xl ${busy ? "opacity-50" : ""}`} />
        <span
          aria-hidden="true"
          className="absolute inset-0 grid place-items-center rounded-full bg-slate-950/50 text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100 group-disabled:opacity-0"
        >
          <Camera className="size-5" />
        </span>
      </button>
      <input
        ref={input}
        type="file"
        accept="image/*"
        onChange={(e) => void onFile(e)}
        className="hidden"
        tabIndex={-1}
        aria-hidden="true"
      />

      <div className="min-w-0">
        <p className="truncate text-lg font-semibold">{user?.name ?? "—"}</p>
        <p className="truncate text-sm text-slate-500 dark:text-slate-400">{user?.email}</p>
        <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm font-medium">
          <button
            type="button"
            onClick={() => input.current?.click()}
            disabled={busy}
            className="text-indigo-600 hover:underline disabled:cursor-progress disabled:opacity-60 dark:text-indigo-400"
          >
            {uploading ? "Enviando…" : hasPhoto ? "Trocar foto" : "Adicionar foto"}
          </button>
          {hasPhoto && (
            <button
              type="button"
              onClick={removePhoto}
              disabled={busy}
              className="text-slate-500 hover:text-red-700 hover:underline disabled:cursor-not-allowed disabled:opacity-60 dark:text-slate-400 dark:hover:text-red-300"
            >
              {remove.isPending ? "Removendo…" : "Remover"}
            </button>
          )}
        </div>
        {error && (
          <p role="alert" className="mt-1.5 text-sm text-red-700 dark:text-red-300">
            {error}
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * Linha da conta. Desktop: rótulo (coluna fixa) | valor | ação à direita.
 * Celular: rótulo e ação na primeira linha, valor embaixo.
 */
function AccountRow({ label, children, action }: { label: string; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1.5 py-4 sm:grid-cols-[10rem_1fr_auto] sm:gap-x-6">
      <dt className="col-start-1 row-start-1 text-sm text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="col-span-2 row-start-2 min-w-0 font-medium sm:col-span-1 sm:col-start-2 sm:row-start-1">
        {children}
      </dd>
      {/* sem ação, a célula some; os valores continuam na mesma coluna */}
      <div className="col-start-2 row-start-1 justify-self-end empty:hidden sm:col-start-3">{action}</div>
    </div>
  );
}

const rowButton =
  "inline-flex h-9 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-3.5 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";

const inputClass =
  "h-10 w-full rounded-[10px] border border-slate-200 bg-white px-3 text-[15px] outline-none transition focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 dark:border-slate-800 dark:bg-slate-950";

function PositionsPicker({
  catalog,
  selected,
  onChange,
}: {
  catalog: CatalogPosition[];
  selected: string[];
  onChange: (ids: string[]) => void;
}) {
  const [query, setQuery] = useState("");
  const byId = useMemo(() => new Map(catalog.map((p) => [p.id, p])), [catalog]);
  const full = selected.length >= MAX_POSITIONS;

  const suggestions = useMemo(() => {
    const q = normalizeForSearch(query.trim());
    if (!q) {
      return [];
    }
    // "enferm" → "Enfermeiro" antes de "Auxiliar de Enfermagem"; o catálogo já vem em ordem alfabética
    const rank = (name: string) => {
      const n = normalizeForSearch(name);
      if (n.startsWith(q)) return 0;
      if (n.split(/[\s/-]+/).some((word) => word.startsWith(q))) return 1;
      return n.includes(q) ? 2 : -1;
    };
    return catalog
      .filter((p) => !selected.includes(p.id))
      .map((p) => ({ p, score: rank(p.name) }))
      .filter(({ score }) => score >= 0)
      .sort((a, b) => a.score - b.score)
      .slice(0, 8)
      .map(({ p }) => p);
  }, [catalog, query, selected]);

  function add(id: string) {
    if (!full) {
      onChange([...selected, id]);
      setQuery("");
    }
  }

  return (
    <div className="mt-6">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-medium">Cargos de interesse</h3>
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {selected.length} de {MAX_POSITIONS}
        </span>
      </div>

      {selected.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-2">
          {selected.map((id) => (
            <li key={id}>
              <span className="inline-flex items-center gap-1 rounded-full bg-indigo-50 py-1 pr-1 pl-3 text-sm font-medium text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">
                {displayPositionName(byId.get(id)?.name ?? `#${id}`)}
                <button
                  type="button"
                  onClick={() => onChange(selected.filter((x) => x !== id))}
                  aria-label={`Remover ${byId.get(id)?.name ?? id}`}
                  className="grid size-6 place-items-center rounded-full hover:bg-indigo-100 dark:hover:bg-indigo-500/25"
                >
                  <X className="size-3.5" aria-hidden="true" />
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}

      <div className="relative mt-3">
        <Search
          className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        />
        <input
          type="search"
          value={query}
          disabled={full}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && suggestions[0]) {
              e.preventDefault();
              add(suggestions[0].id);
            }
          }}
          placeholder={full ? "Limite de cargos atingido" : "Buscar cargo (ex.: enfermeiro, analista, professor)"}
          aria-label="Buscar cargo"
          className="h-11 w-full rounded-[10px] border border-slate-200 bg-white pr-3 pl-10 text-[15px] outline-none transition placeholder:text-slate-400 focus:border-indigo-600 focus:ring-4 focus:ring-indigo-600/20 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-800 dark:bg-slate-950"
        />
      </div>

      {query.trim() && (
        <ul className="mt-2 overflow-hidden rounded-[10px] border border-slate-200 dark:border-slate-800">
          {suggestions.length === 0 ? (
            <li className="px-4 py-3 text-sm text-slate-500">Nenhum cargo encontrado.</li>
          ) : (
            suggestions.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => add(p.id)}
                  className="flex w-full items-center justify-between px-4 py-2.5 text-left text-sm hover:bg-slate-50 dark:hover:bg-slate-800/60"
                >
                  {displayPositionName(p.name)}
                  <Plus className="size-4 text-slate-400" aria-hidden="true" />
                </button>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}

/** Níveis de vaga, como as UFs: nenhum marcado = todos. */
function EducationPicker({
  selected,
  onChange,
}: {
  selected: EducationLevel[];
  onChange: (levels: EducationLevel[]) => void;
}) {
  function toggle(level: EducationLevel) {
    onChange(selected.includes(level) ? selected.filter((x) => x !== level) : [...selected, level]);
  }

  return (
    <div className="mt-8">
      <h3 className="font-medium">Escolaridade das vagas</h3>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        {selected.length === 0
          ? "Nenhuma marcada: você vê vagas de qualquer nível."
          : "Concursos sem escolaridade informada aparecem sempre."}
      </p>
      <div className="mt-4 flex flex-wrap gap-2">
        {EDUCATION_LEVELS.map(({ value, label }) => {
          const on = selected.includes(value);
          return (
            <button
              key={value}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(value)}
              className={`h-9 rounded-lg border px-4 text-sm font-semibold transition ${
                on
                  ? "border-indigo-600 bg-indigo-600 text-white dark:border-indigo-500 dark:bg-indigo-500"
                  : "border-slate-200 bg-white text-slate-700 hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300 dark:hover:border-slate-600"
              }`}
            >
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function UfPicker({ selected, onChange }: { selected: string[]; onChange: (ufs: string[]) => void }) {
  function toggle(uf: string) {
    onChange(selected.includes(uf) ? selected.filter((x) => x !== uf) : [...selected, uf]);
  }

  return (
    <div className="mt-8">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-medium">Estados de interesse</h3>
        {selected.length > 0 && (
          <button
            type="button"
            onClick={() => onChange([])}
            className="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            Limpar
          </button>
        )}
      </div>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        {selected.length === 0
          ? "Nenhum marcado: você vê concursos do Brasil inteiro."
          : "Concursos nacionais aparecem sempre."}
      </p>

      <div className="mt-4 grid gap-4">
        {REGIONS.map((region) => (
          <div key={region.name} className="grid gap-2 sm:grid-cols-[120px_1fr] sm:items-center">
            <span className="text-sm text-slate-500 dark:text-slate-400">{region.name}</span>
            <div className="flex flex-wrap gap-2">
              {region.ufs.map((uf) => {
                const on = selected.includes(uf);
                return (
                  <button
                    key={uf}
                    type="button"
                    aria-pressed={on}
                    onClick={() => toggle(uf)}
                    className={`h-9 min-w-12 rounded-lg border px-3 text-sm font-semibold transition ${
                      on
                        ? "border-indigo-600 bg-indigo-600 text-white dark:border-indigo-500 dark:bg-indigo-500"
                        : "border-slate-200 bg-white text-slate-700 hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300 dark:hover:border-slate-600"
                    }`}
                  >
                    {uf}
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Resumo diário por e-mail. Salva na hora (não entra na barra de salvar das preferências). O
 * sininho no app vale sempre.
 */
function NotificationSettings() {
  const auth = useAuth();
  const profile = useProfile();
  const change = useChangeEmailDigest();
  const enabled = change.isPending ? change.variables : (profile.data?.emailDigest ?? true);
  const verified = Boolean(auth.user?.profile.email_verified);

  return (
    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="text-lg font-semibold">Notificações</h2>
      <div className="mt-4 flex items-start justify-between gap-6">
        <div>
          <p id="email-digest-label" className="font-medium">
            Resumo diário por e-mail
          </p>
          <p id="email-digest-hint" className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Uma vez por dia, de manhã, com os concursos novos para os seus cargos. Só quando houver novidade.
          </p>
          {!verified && (
            <p className="mt-2 text-sm text-amber-700 dark:text-amber-300">
              Confirme seu e-mail para receber o resumo.
            </p>
          )}
          {change.isError && (
            <p className="mt-2 text-sm text-red-700 dark:text-red-300">Não foi possível salvar agora.</p>
          )}
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={enabled}
          aria-labelledby="email-digest-label"
          aria-describedby="email-digest-hint"
          disabled={profile.isPending || change.isPending}
          onClick={() => change.mutate(!enabled)}
          className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition focus-visible:ring-4 focus-visible:ring-indigo-600/30 focus-visible:outline-none disabled:cursor-progress disabled:opacity-60 ${
            enabled ? "bg-indigo-600 dark:bg-indigo-500" : "bg-slate-300 dark:bg-slate-700"
          }`}
        >
          <span
            aria-hidden="true"
            className={`inline-block size-5 rounded-full bg-white shadow transition-transform ${enabled ? "translate-x-6" : "translate-x-1"}`}
          />
        </button>
      </div>
    </section>
  );
}

/** Barra fixa no rodapé: aparece quando há mudança não salva (ou logo depois de salvar). */
function SaveBar({
  dirty,
  status,
  onSave,
  onDiscard,
}: {
  dirty: boolean;
  status: SaveStatus;
  onSave: () => void;
  onDiscard: () => void;
}) {
  if (!dirty && status.state !== "saved") {
    return null;
  }
  return (
    <div className="bottom-dock fixed inset-x-0 z-10 border-t sm:bottom-0 border-slate-200 bg-white/90 backdrop-blur dark:border-slate-800 dark:bg-slate-950/90">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <p className="text-sm" role="status">
          {status.state === "saved" && !dirty && (
            <span className="inline-flex items-center gap-1.5 text-emerald-700 dark:text-emerald-400">
              <Check className="size-4" aria-hidden="true" />
              Preferências salvas
            </span>
          )}
          {status.state === "error" && <span className="text-red-700 dark:text-red-300">{status.message}</span>}
          {dirty && status.state !== "error" && (
            <span className="text-slate-600 dark:text-slate-400">Você tem alterações não salvas.</span>
          )}
        </p>
        {dirty && (
          <div className="flex gap-2">
            <button type="button" onClick={onDiscard} className={secondaryButton}>
              Descartar
            </button>
            <button
              type="button"
              onClick={onSave}
              disabled={status.state === "saving"}
              className="inline-flex h-10 items-center rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400"
            >
              {status.state === "saving" ? "Salvando…" : "Salvar preferências"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

const secondaryButton =
  "inline-flex h-10 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-800 transition hover:border-slate-400 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 dark:hover:border-slate-600";
