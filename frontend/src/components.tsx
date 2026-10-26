import { useEffect, useRef, type ReactNode } from "react";
import {
  AlertCircle,
  ArrowDownToLine,
  ArrowRight,
  Check,
  Clock3,
  FileArchive,
  File,
  Image,
  Inbox,
  LoaderCircle,
  X,
} from "lucide-react";
import {
  statusLabels,
  type TaskStatus,
  type Task,
  type Executor,
} from "./types";

import { useTaskCatalog } from "./TaskCatalog";

export const number = (value: number) => new Intl.NumberFormat().format(value);
export function bytes(value: number | null) {
  if (value === null) return "—";
  if (value < 1024) return `${value} B`;
  const i = Math.min(Math.floor(Math.log(value) / Math.log(1024)), 3);
  return `${(value / 1024 ** i).toFixed(i === 1 ? 0 : 1)} ${["B", "KB", "MB", "GB"][i]}`;
}
export function date(value: string | null, full = false) {
  if (!value) return "—";
  return new Date(value).toLocaleString(
    undefined,
    full
      ? { dateStyle: "medium", timeStyle: "medium" }
      : { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" },
  );
}
export function Logo({ small = false }: { small?: boolean }) {
  return (
    <span className={`brand ${small ? "small" : ""}`}>
      <span className="brand-symbol" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M6 4v16M18 4l-9 8 9 8" />
        </svg>
      </span>
      {!small && (
        <span>
          krite<span className="brand-period">.</span>
        </span>
      )}
    </span>
  );
}
export function Status({ status }: { status: TaskStatus }) {
  const color =
    status === "COMPLETE"
      ? "green"
      : status === "FAILED"
        ? "red"
        : status === "IN_PROGRESS"
          ? "blue"
          : "amber";
  return (
    <span className={`badge ${color}`}>
      <span className="status-dot" />
      {statusLabels[status]}
    </span>
  );
}
export function ErrorNotice({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <div className="error-notice" role="alert">
      <AlertCircle size={17} />
      <span>{message}</span>
      {retry && <button onClick={retry}>Retry</button>}
    </div>
  );
}
export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Inbox size={24} strokeWidth={1.5} />
      </span>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  );
}
export function Loading({ text = "Loading workspace…" }: { text?: string }) {
  return (
    <div className="loading-state" role="status">
      <LoaderCircle className="spin" size={22} />
      <span>{text}</span>
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      className={wide ? "modal wide" : "modal"}
      aria-label={title}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div className="modal-heading">
        <h2>{title}</h2>
        <button
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
          disabled={busy}
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function TaskIcon({
  task,
  size = 18,
}: {
  task: Pick<Task, "type">;
  size?: number;
}) {
  const { byId } = useTaskCatalog();
  const icon = byId[task.type]?.icon;
  if (icon === "archive") return <FileArchive size={size} />;
  if (icon === "image") return <Image size={size} />;
  return <File size={size} />;
}
export function TaskTable({
  tasks,
  admin,
  onDetail,
  onDownload,
  downloading,
}: {
  tasks: Task[];
  admin: boolean;
  onDetail: (task: Task) => void;
  onDownload: (task: Task) => void;
  downloading: number | null;
}) {
  const { byId } = useTaskCatalog();
  return (
    <div className="table-scroll">
      <table className="task-table">
        <thead>
          <tr>
            <th>Task / file</th>
            <th>Status</th>
            {admin && <th>Submitted by</th>}
            <th>Executor</th>
            <th>Submitted</th>
            <th>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <td>
                <button className="task-link" onClick={() => onDetail(task)}>
                  <span className="file-icon">
                    <TaskIcon task={task} />
                  </span>
                  <span className="task-name">
                    <strong title={task.filename}>{task.filename}</strong>
                    <span>
                      #{String(task.id).padStart(4, "0")}
                      <span className="separator">·</span>
                      {byId[task.type]?.label || task.type}
                    </span>
                  </span>
                </button>
              </td>
              <td>
                <Status status={task.status} />
              </td>
              {admin && (
                <td className="owner-cell">
                  <span className="mini-avatar">
                    {task.owner?.slice(0, 1).toUpperCase() || "?"}
                  </span>
                  {task.owner || "—"}
                </td>
              )}
              <td>
                <span className="mono muted">
                  {task.executorId || "Unassigned"}
                </span>
              </td>
              <td className="date-cell">{date(task.createdAt)}</td>
              <td>
                {task.resultAvailable ? (
                  <button
                    className="icon-button"
                    aria-label={`Download ${task.filename}`}
                    disabled={downloading === task.id}
                    onClick={() => onDownload(task)}
                  >
                    {downloading === task.id ? (
                      <LoaderCircle size={17} className="spin" />
                    ) : (
                      <ArrowDownToLine size={17} />
                    )}
                  </button>
                ) : (
                  <button
                    className="icon-button"
                    aria-label={`View ${task.filename}`}
                    onClick={() => onDetail(task)}
                  >
                    <ArrowRight size={17} />
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function Meter({
  value,
  label,
  detail,
  stale = false,
}: {
  value: number;
  label: string;
  detail: string;
  stale?: boolean;
}) {
  const percent = Math.round(Math.max(0, Math.min(100, value)));
  return (
    <div className={`meter ${stale ? "stale" : ""}`}>
      <div className="meter-label">
        <span>{label}</span>
        <strong>{detail}</strong>
      </div>
      <div
        className="meter-track"
        role="meter"
        aria-label={label}
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span
          style={{ width: `${percent}%` }}
          className={percent > 85 ? "high" : ""}
        />
      </div>
    </div>
  );
}
export function ExecutorCard({ executor: e }: { executor: Executor }) {
  const memory = e.memoryTotal ? (1 - e.memoryFree / e.memoryTotal) * 100 : 0;
  return (
    <article className={`executor-card ${!e.online ? "is-offline" : ""}`}>
      <div className="executor-card-heading">
        <span className="node-glyph" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
        <div>
          <h3>{e.id}</h3>
          <p>
            {e.cpuCores} cores · {bytes(e.memoryTotal)} memory
          </p>
        </div>
        <span className={`badge ${e.online ? "green" : "neutral"}`}>
          <span className="status-dot" />
          {e.online ? "Online" : "Offline"}
        </span>
      </div>
      <Meter
        label={e.online ? "CPU usage" : "CPU · last reported"}
        value={e.cpuLoad * 100}
        detail={`${Math.round(e.cpuLoad * 100)}%`}
        stale={!e.online}
      />
      <Meter
        label={e.online ? "Memory" : "Memory · last reported"}
        value={memory}
        detail={`${bytes(e.memoryTotal - e.memoryFree)} / ${bytes(e.memoryTotal)}`}
        stale={!e.online}
      />
      <div className="executor-card-footer">
        <span>
          <span className={`status-dot ${e.runningTasks ? "blue-dot" : ""}`} />
          {e.runningTasks} running<span className="separator">·</span>
          {e.queuedTasks} queued
        </span>
        <span title={date(e.lastHeartbeat, true)}>
          {e.online ? (
            <>
              <Check size={13} /> Heartbeat received
            </>
          ) : (
            <>
              <Clock3 size={13} /> No recent heartbeat
            </>
          )}
        </span>
      </div>
    </article>
  );
}
