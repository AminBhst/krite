import { createContext, useContext, useMemo, type ReactNode } from "react";
import { api } from "./api";
import { usePolling } from "./hooks";
import type { TaskTypeInfo } from "./types";

interface Catalog {
  types: TaskTypeInfo[];
  byId: Record<string, TaskTypeInfo | undefined>;
  loading: boolean;
  error?: string;
  refresh: () => void;
}
const Context = createContext<Catalog | null>(null);

export function TaskCatalogProvider({ children }: { children: ReactNode }) {
  const result = usePolling("task-catalog", api.types, 60_000);
  const value = useMemo(
    () => ({
      types: result.data ?? [],
      byId: Object.fromEntries(
        (result.data ?? []).map((type) => [type.id, type]),
      ),
      loading: result.loading,
      error: result.error,
      refresh: result.refresh,
    }),
    [result.data, result.loading, result.error, result.refresh],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useTaskCatalog() {
  const catalog = useContext(Context);
  if (!catalog)
    throw new Error("Task catalog must be used inside its provider.");
  return catalog;
}
