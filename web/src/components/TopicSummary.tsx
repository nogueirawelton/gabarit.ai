import { LoaderCircle, Sparkles } from "lucide-react";

import { ApiError } from "../api";
import { useRequestSummary, useTopicSummary } from "../data/topics";

/**
 * Resumo do tópico pela IA: pedir, gerando, falhou ou pronto (o que estudar, o que mais cai,
 * pegadinhas, as leis com link e leituras). Compartilhado: quem pediu antes já deixa pronto.
 */
export function TopicSummaryPanel({ targetId, subject, topic }: { targetId: string; subject: number; topic: number }) {
  const summary = useTopicSummary(targetId, subject, topic, true);
  const request = useRequestSummary(targetId, subject, topic);
  const data = summary.data;
  const ask = (label: string) => (
    <button type="button" onClick={() => request.mutate()} disabled={request.isPending} className={primaryButton}>
      <Sparkles className="size-4" aria-hidden="true" />
      {request.isPending ? "Pedindo…" : label}
    </button>
  );

  let body;
  if (summary.isPending) {
    body = <p className="text-slate-500 dark:text-slate-400">Carregando…</p>;
  } else if (!data) {
    body = (
      <div>
        <p className="text-slate-600 dark:text-slate-400">
          A IA escreve um resumo do tópico com o que mais cai, as pegadinhas e as leis que ele cobra.
        </p>
        {ask("Gerar resumo")}
      </div>
    );
  } else if (data.status === "PENDING") {
    body = (
      <p className="flex items-center gap-2 text-slate-600 dark:text-slate-400" role="status">
        <LoaderCircle className="size-4 animate-spin text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        Escrevendo o resumo…
      </p>
    );
  } else if (data.status === "FAILED") {
    body = (
      <div className="text-slate-600 dark:text-slate-400">
        <p>Não conseguimos escrever o resumo{data.retryable ? " agora" : ""}.</p>
        {data.retryable && ask("Tentar de novo")}
      </div>
    );
  } else {
    body = (
      <div className="space-y-3 text-slate-700 dark:text-slate-300">
        <p className="leading-relaxed whitespace-pre-line">{data.summary}</p>
        <SummaryList title="O que mais cai" items={data.keyPoints} />
        <SummaryList title="Pegadinhas" items={data.pitfalls} />
        {data.laws.length > 0 && (
          <div>
            <p className="font-semibold text-slate-900 dark:text-slate-100">Legislação</p>
            <ul className="mt-1 space-y-0.5">
              {data.laws.map((law) => (
                <li key={law.name}>
                  <a
                    href={law.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-medium text-indigo-700 hover:underline dark:text-indigo-300"
                  >
                    {law.name}
                  </a>
                  {law.articles.length > 0 && (
                    <span className="text-slate-500 dark:text-slate-400"> · {law.articles.join(", ")}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
        <SummaryList
          title="Para aprofundar"
          items={data.readings.map((reading) => `${reading.author}, ${reading.title}`)}
        />
        <p className="text-xs text-slate-500 dark:text-slate-400">
          Escrito por IA: confira na lei e no edital (a lei pode ter mudado depois que o modelo foi treinado).
        </p>
      </div>
    );
  }

  return (
    <div className="mt-2 mb-1 rounded-lg bg-slate-50 p-3 text-sm dark:bg-slate-800/50">
      {body}
      {request.isError && (
        <p role="alert" className="mt-2 text-red-700 dark:text-red-300">
          {request.error instanceof ApiError && request.error.detail ? request.error.detail : "Não foi possível pedir agora."}
        </p>
      )}
    </div>
  );
}

function SummaryList({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <div>
      <p className="font-semibold text-slate-900 dark:text-slate-100">{title}</p>
      <ul className="mt-1 list-disc space-y-0.5 pl-5">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

const primaryButton =
  "mt-3 inline-flex h-9 items-center gap-1.5 rounded-[10px] bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-progress disabled:opacity-70 dark:bg-indigo-500 dark:hover:bg-indigo-400";
