import { useCallback, useEffect, useRef, useState } from "react";

export function usePolling<T>(
  key: string,
  fetcher: (signal: AbortSignal) => Promise<T>,
  interval = 5000,
): {
  data?: T;
  error?: string;
  loading: boolean;
  at?: Date;
  refresh: () => void;
} {
  const [state, setState] = useState<{
    key: string;
    data?: T;
    error?: string;
    loading: boolean;
    at?: Date;
  }>({ key, loading: true });
  const [revision, setRevision] = useState(0);
  const fetchRef = useRef(fetcher);
  fetchRef.current = fetcher;
  useEffect(() => {
    const controller = new AbortController();
    let timeout: ReturnType<typeof setTimeout>;
    setState((previous) =>
      previous.key === key
        ? { ...previous, loading: !previous.data }
        : { key, loading: true },
    );
    const poll = async () => {
      try {
        const data = await fetchRef.current(controller.signal);
        if (!controller.signal.aborted)
          setState({ key, data, loading: false, at: new Date() });
      } catch (error) {
        if (!controller.signal.aborted)
          setState((previous) => ({
            ...previous,
            key,
            loading: false,
            error:
              error instanceof Error ? error.message : "Something went wrong.",
          }));
      }
      if (!controller.signal.aborted) timeout = setTimeout(poll, interval);
    };
    void poll();
    return () => {
      controller.abort();
      clearTimeout(timeout);
    };
  }, [key, revision, interval]);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  return { ...(state.key === key ? state : { loading: true }), refresh };
}

export function useDebounce<T>(value: T, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}
