import { useEffect, useState } from "react";

/** Texto que só vale depois que a pessoa para de digitar: não busca a cada letra. */
export function useDebounced(value: string, delayMs = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
