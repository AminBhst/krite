export type Role = "ADMIN" | "USER";
export interface Account {
  username: string;
  role: Role;
}
// Operation identifiers and metadata are supplied by the API.
export type TaskType = string;
export interface TaskOption {
  name: string;
  label: string;
  kind: "select" | "boolean";
  choices: string[];
  defaultValue: string;
}
export interface TaskTypeInfo {
  id: TaskType;
  label: string;
  description: string;
  output: string;
  acceptedMimeTypes: string[];
  inputDescription: string;
  helpText: string;
  icon: string;
  maxFileSizeBytes: number | null;
  options?: TaskOption[];
}
export type TaskStatus =
  | "INITIAL"
  | "IN_QUEUE"
  | "IN_COORDINATOR_QUEUE"
  | "IN_EXECUTOR_QUEUE"
  | "IN_PROGRESS"
  | "COMPLETE"
  | "FAILED";
export interface Task {
  id: number;
  type: TaskType;
  status: TaskStatus;
  filename: string;
  fileSize: number | null;
  owner: string;
  executorId: string | null;
  createdAt: string;
  updatedAt: string | null;
  resultAvailable: boolean;
  textPreviewAvailable?: boolean;
  artifacts?: { path: string; label: string }[];
  options?: Record<string, string>;
}
export interface TaskPage {
  content: Task[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export interface Summary {
  counts: Record<TaskStatus, number>;
  submissions: { time: string; count: number }[];
  generatedAt: string;
}
export interface Executor {
  id: string;
  hostname: string;
  cpuCores: number;
  cpuLoad: number;
  memoryTotal: number;
  memoryFree: number;
  lastHeartbeat: string | null;
  online: boolean;
  runningTasks: number;
  queuedTasks: number;
}
export const statusLabels: Record<TaskStatus, string> = {
  INITIAL: "Pending",
  IN_QUEUE: "Queued",
  IN_COORDINATOR_QUEUE: "Scheduling",
  IN_EXECUTOR_QUEUE: "Queued on executor",
  IN_PROGRESS: "Running",
  COMPLETE: "Completed",
  FAILED: "Failed",
};
export const queueStatuses: TaskStatus[] = [
  "INITIAL",
  "IN_QUEUE",
  "IN_COORDINATOR_QUEUE",
  "IN_EXECUTOR_QUEUE",
];
