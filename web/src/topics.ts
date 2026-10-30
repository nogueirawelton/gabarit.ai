// Tópicos do conteúdo programático: a página de cada um e o nome curto (os do edital podem ser longos).

/** Página do tópico (ordinais a partir de 0, na ordem do conteúdo). */
export function topicPath(targetId: string, subject: number, topic: number) {
  return `/meus-concursos/${targetId}/topicos/${subject}/${topic}`;
}

/**
 * Nome curto do tópico: até os dois-pontos ou o primeiro ponto ("Arquitetura e organização de
 * computadores: sistemas de numeração…" → "Arquitetura e organização de computadores"), no máximo 70
 * letras.
 */
export function shortTopic(text: string) {
  const head = text.split(/[:;]|\.\s/)[0].trim().replace(/\.$/, "");
  const short = head.length >= 4 ? head : text;
  return short.length > 70 ? `${short.slice(0, 69)}…` : short;
}

/**
 * Ordinais (disciplina, tópico) de um tópico pelo texto, como o plano guarda. Sem disciplina (revisão
 * da semana): procura em todas. null se o conteúdo mudou e o tópico não está mais nele.
 */
export function findTopic(
  subjects: { name: string; topics: string[] }[],
  subject: string | null,
  topic: string,
): [number, number] | null {
  for (let s = 0; s < subjects.length; s++) {
    if (subject !== null && subjects[s].name !== subject) {
      continue;
    }
    const t = subjects[s].topics.indexOf(topic);
    if (t >= 0) {
      return [s, t];
    }
  }
  return null;
}
