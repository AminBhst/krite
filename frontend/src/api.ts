import type {
  Account,
  Task,
  TaskPage,
  TaskType,
  TaskTypeInfo,
  Summary,
  Executor,
} from "./types";

const TOKEN_KEY = "krite.session";
let token = sessionStorage.getItem(TOKEN_KEY);
export function setToken(value: string | null) {
  token = value;
  if (value) sessionStorage.setItem(TOKEN_KEY, value);
  else sessionStorage.removeItem(TOKEN_KEY);
}
export const hasSession = () => Boolean(token);

async function errorMessage(response: Response) {
  const text = await response.text();
  try {
    const data = JSON.parse(text);
    return (
      data.message ||
      (response.status === 401
        ? "Your session has expired. Please sign in again."
        : data.error)
    );
  } catch {
    return text && !text.startsWith("<")
      ? text
      : `Request failed (${response.status}). Please try again.`;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      signal: init.signal
        ? AbortSignal.any([init.signal, AbortSignal.timeout(15_000)])
        : AbortSignal.timeout(15_000),
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw error;
    throw new Error(
      "Cannot reach the API. Check that the API module is running, then retry.",
    );
  }
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/auth/")) {
      setToken(null);
      window.dispatchEvent(new Event("krite:session-expired"));
    }
    throw new Error(await errorMessage(response));
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string }>("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    }),
  register: async (username: string, password: string) => {
    const response = await fetch("/auth/register", {
      method: "POST",
      signal: AbortSignal.timeout(15_000),
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) throw new Error(await errorMessage(response));
  },
  me: () => request<Account>("/api/me"),
  summary: (signal?: AbortSignal) =>
    request<Summary>("/api/dashboard", { signal }),
  executors: (signal?: AbortSignal) =>
    request<Executor[]>("/admin/executors", { signal }),
  tasks: (params: URLSearchParams, signal?: AbortSignal) =>
    request<TaskPage>(`/api/task?${params}`, { signal }),
  task: (id: number, signal?: AbortSignal) =>
    request<Task>(`/api/task/${id}`, { signal }),
  types: (signal?: AbortSignal) =>
    request<TaskTypeInfo[]>("/api/task/types", { signal }),
  upload: (type: TaskType, file: File, onProgress: (percent: number) => void, options: Record<string, string> = {}) =>
    new Promise<Task>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", `/api/task/${encodeURIComponent(type)}`);
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      xhr.timeout = 180_000;
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable)
          onProgress(Math.round((event.loaded / event.total) * 100));
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            resolve(JSON.parse(xhr.responseText));
          } catch {
            reject(
              new Error(
                "The API returned an invalid response. Check your task list before retrying.",
              ),
            );
          }
        } else {
          if (xhr.status === 401)
            window.dispatchEvent(new Event("krite:session-expired"));
          let message = `Upload failed (${xhr.status}). Please try again.`;
          try {
            message = JSON.parse(xhr.responseText).message || message;
          } catch {
            /* use fallback */
          }
          reject(new Error(message));
        }
      };
      xhr.onerror = () =>
        reject(
          new Error("Upload interrupted. Check your connection and try again."),
        );
      xhr.ontimeout = () =>
        reject(
          new Error("Upload timed out. Check your task list before retrying."),
        );
      const data = new FormData();
      data.append("file", file);
      data.append("options", JSON.stringify(options));
      xhr.send(data);
    }),
  preview: (id: number, signal?: AbortSignal) =>
    request<{ text: string; truncated: boolean }>(`/api/task/${id}/preview`, { signal }),
  download: async (task: Task, artifact = "result") => {
    const response = await fetch(`/api/task/${task.id}/${encodeURIComponent(artifact)}`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(180_000),
    });
    if (!response.ok) {
      if (response.status === 401)
        window.dispatchEvent(new Event("krite:session-expired"));
      throw new Error(await errorMessage(response));
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = resultFilename(
      response.headers.get("Content-Disposition"),
      `task-${task.id}-result`,
    );
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  },
};

// Prefer the UTF-8 filename supplied by the server; never infer an operation's extension.
function resultFilename(disposition: string | null, fallback: string): string {
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try {
      return decodeURIComponent(encoded.trim());
    } catch {
      /* Try the plain filename. */
    }
  }
  return (
    disposition?.match(/filename="([^"\r\n]+)"/i)?.[1] ||
    disposition?.match(/filename=([^;\r\n]+)/i)?.[1]?.trim() ||
    fallback
  );
}
