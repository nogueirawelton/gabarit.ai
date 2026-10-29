import type { EducationLevel } from "./api";

/** Estados por região, na ordem em que aparecem na tela de perfil. */
export const REGIONS: { name: string; ufs: string[] }[] = [
  { name: "Norte", ufs: ["AC", "AP", "AM", "PA", "RO", "RR", "TO"] },
  { name: "Nordeste", ufs: ["AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE"] },
  { name: "Centro-Oeste", ufs: ["DF", "GO", "MT", "MS"] },
  { name: "Sudeste", ufs: ["ES", "MG", "RJ", "SP"] },
  { name: "Sul", ufs: ["PR", "RS", "SC"] },
];

/** Níveis de escolaridade das vagas, na ordem em que aparecem na tela. */
export const EDUCATION_LEVELS: { value: EducationLevel; label: string }[] = [
  { value: "FUNDAMENTAL", label: "Fundamental" },
  { value: "MEDIO", label: "Médio" },
  { value: "TECNICO", label: "Técnico" },
  { value: "SUPERIOR", label: "Superior" },
];

const LOWERCASE_WORDS = new Set(["a", "e", "o", "da", "de", "do", "das", "dos", "em", "na", "no", "para"]);

/**
 * Parte do catálogo do PCI vem em caixa alta ("AGENTE DE ENDEMIAS"): mostra como "Agente de Endemias".
 * Nome que já vem com caixa mista fica como está.
 */
export function displayPositionName(name: string): string {
  if (name !== name.toUpperCase()) {
    return name;
  }
  return name
    .toLowerCase()
    .split(" ")
    .map((word, i) => (i > 0 && LOWERCASE_WORDS.has(word) ? word : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(" ");
}

/** Busca sem acento e sem caixa: "medico" acha "Médico". */
export function normalizeForSearch(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}
