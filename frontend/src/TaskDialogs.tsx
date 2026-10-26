import { useEffect, useState, type FormEvent } from "react";
import {
  ArrowDownToLine,
  ArrowRight,
  Check,
  CheckCircle2,
  File,
  LoaderCircle,
  UploadCloud,
  X,
} from "lucide-react";
import { api } from "./api";
import {
  bytes,
  date,
  ErrorNotice,
  Loading,
  Modal,
  Status,
  TaskIcon,
} from "./components";
import { usePolling } from "./hooks";
import { useTaskCatalog } from "./TaskCatalog";
import { statusLabels, type Task, type TaskType } from "./types";

export function NewTaskDialog({
  initialType,
  onClose,
  onCreated,
}: {
  initialType?: TaskType;
  onClose: () => void;
  onCreated: (task: Task) => void;
}) {
  const catalog = useTaskCatalog();
  const [type, setType] = useState(initialType || "");
  const definition = catalog.byId[type];
  const [options, setOptions] = useState<Record<string, string>>({});
  // Reconcile choices with refreshed metadata; removed models/options never get submitted.
  const selectedOptions = Object.fromEntries((definition?.options || []).map(field => {
    const value = options[field.name] ?? field.defaultValue;
    return [field.name, field.kind === "select" && !field.choices.includes(value) ? field.defaultValue : value];
  }));
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [preview, setPreview] = useState<string>();
  useEffect(() => {
    if (!catalog.loading && !catalog.error && !catalog.byId[type]) {
      setType(catalog.types[0]?.id || "");
      setFile(null);
    }
  }, [catalog, type]);
  useEffect(() => {
    if (!file || !file.type.startsWith("image/")) {
      setPreview(undefined);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  function validate(candidate: File): string {
    if (!definition)
      return "Operation details are not available. Refresh the catalog and retry.";
    if (!candidate.size) return "Choose a non-empty file.";
    if (
      definition.maxFileSizeBytes !== null &&
      candidate.size > definition.maxFileSizeBytes
    )
      return `This file is too large. Choose a file up to ${bytes(definition.maxFileSizeBytes)}.`;
    const accepted = definition.acceptedMimeTypes;
    if (
      accepted.length &&
      !accepted.some(
        (mime) =>
          mime === candidate.type ||
          mime === "*/*" ||
          (mime.endsWith("/*") && candidate.type.startsWith(mime.slice(0, -1))),
      )
    )
      return `Choose a file matching: ${definition.inputDescription}.`;
    return "";
  }
  const chooseFile = (candidate?: File) => {
    if (!candidate || busy || catalog.error) return;
    const message = validate(candidate);
    setError(message);
    setFile(message ? null : candidate);
  };
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file || busy || !definition || catalog.error) return;
    const message = validate(file);
    if (message) {
      setError(message);
      return;
    }
    setBusy(true);
    setError("");
    setProgress(0);
    try {
      onCreated(await api.upload(type, file, setProgress, selectedOptions));
    } catch (error) {
      setError(
        error instanceof Error ? error.message : "Could not submit the task.",
      );
      setBusy(false);
    }
  }
  return (
    <Modal title="Create a task" onClose={onClose} busy={busy}>
      <form onSubmit={submit}>
        <div className="modal-body">
          <p className="modal-intro">
            Choose an operation and a file. Krite will take care of the
            processing.
          </p>
          {catalog.loading && <Loading text="Loading supported operations…" />}
          {catalog.error && (
            <ErrorNotice
              message={`Could not load operations. ${catalog.error}`}
              retry={catalog.refresh}
            />
          )}
          {!catalog.loading && !catalog.error && !catalog.types.length && (
            <p>No operations are currently available.</p>
          )}
          <fieldset
            className="type-fieldset"
            disabled={busy || Boolean(catalog.error)}
          >
            <legend>
              01 <span>Choose an operation</span>
            </legend>
            <div className="type-options">
              {catalog.types.map((operation) => (
                <label
                  key={operation.id}
                  className={`type-option ${type === operation.id ? "selected" : ""}`}
                >
                  <input
                    type="radio"
                    name="taskType"
                    value={operation.id}
                    checked={type === operation.id}
                    onChange={() => {
                      setType(operation.id);
                      setOptions({});
                      setFile(null);
                      setError("");
                    }}
                  />
                  <span className="type-option-top">
                    <TaskIcon task={{ type: operation.id }} size={22} />
                    <span className="radio-indicator">
                      {type === operation.id && <span />}
                    </span>
                  </span>
                  <strong>{operation.label}</strong>
                  <span>{operation.description}</span>
                  <small>{operation.output}</small>
                </label>
              ))}
            </div>
          </fieldset>
          <fieldset
            className="upload-fieldset"
            disabled={busy || !definition || Boolean(catalog.error)}
          >
            <legend>
              02 <span>Add your file</span>
            </legend>
            {file ? (
              <div className="selected-file">
                {preview ? (
                  <img
                    className="file-preview"
                    src={preview}
                    alt="Selected file preview"
                  />
                ) : (
                  <span className="file-icon">
                    <File size={24} />
                  </span>
                )}
                <div>
                  <strong>{file.name}</strong>
                  <span>{bytes(file.size)} · Ready to upload</span>
                </div>
                <button
                  type="button"
                  className="icon-button"
                  aria-label="Remove file"
                  onClick={() => setFile(null)}
                >
                  <X size={18} />
                </button>
              </div>
            ) : (
              <label
                className={`dropzone ${dragging ? "dragging" : ""}`}
                onDragOver={(event) => {
                  event.preventDefault();
                  if (!busy && definition) setDragging(true);
                }}
                onDragLeave={() => setDragging(false)}
                onDrop={(event) => {
                  event.preventDefault();
                  setDragging(false);
                  if (event.dataTransfer.files.length > 1) {
                    setError("Choose one file per task.");
                    return;
                  }
                  chooseFile(event.dataTransfer.files[0]);
                }}
              >
                <input
                  type="file"
                  aria-label="Choose file"
                  accept={definition?.acceptedMimeTypes.join(",") || undefined}
                  onChange={(event) => chooseFile(event.target.files?.[0])}
                />
                <span className="upload-glyph">
                  <UploadCloud size={25} strokeWidth={1.5} />
                </span>
                <strong>
                  Drop your file here, or <span>browse files</span>
                </strong>
                <small>
                  {definition?.inputDescription || "Choose an operation first"}
                  {definition?.maxFileSizeBytes != null &&
                    ` · Up to ${bytes(definition.maxFileSizeBytes)}`}
                </small>
              </label>
            )}
          </fieldset>
          {!!definition?.options?.length && (
            <fieldset className="task-options" disabled={busy || Boolean(catalog.error)}>
              <legend>03 <span>Processing options</span></legend>
              {definition.options.map(field => (
                <label key={field.name} className={field.kind === "boolean" ? "option-checkbox" : "option-select"}>
                  {field.kind === "boolean" ? <>
                    <input type="checkbox" checked={selectedOptions[field.name] === "true"}
                      onChange={event => setOptions(previous => ({...previous, [field.name]: String(event.target.checked)}))} />
                    <span>{field.label}</span>
                  </> : <>
                    <span>{field.label}</span>
                    <select value={selectedOptions[field.name]} required
                      onChange={event => setOptions(previous => ({...previous, [field.name]: event.target.value}))}>
                      {field.choices.map(choice => <option key={choice} value={choice}>{choice}</option>)}
                    </select>
                  </>}
                </label>
              ))}
            </fieldset>
          )}
          {definition?.helpText && (
            <div className="subtle-note">
              <CheckCircle2 size={16} />
              <span>{definition.helpText}</span>
            </div>
          )}
          {busy && (
            <div className="upload-progress" role="status">
              <div>
                <span>
                  {progress < 100 ? "Uploading file…" : "Creating your task…"}
                </span>
                <strong>{progress}%</strong>
              </div>
              <progress max={100} value={progress} />
            </div>
          )}
          {error && <ErrorNotice message={error} />}
        </div>
        <div className="modal-footer">
          <button
            type="button"
            className="button secondary"
            disabled={busy}
            onClick={onClose}
          >
            Cancel
          </button>
          <button
            className="button primary"
            disabled={busy || !file || !definition || Boolean(catalog.error)}
          >
            {busy ? (
              <LoaderCircle size={16} className="spin" />
            ) : (
              <ArrowRight size={16} />
            )}
            {busy ? "Submitting…" : "Submit task"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export function TaskDetailDialog({
  task,
  onClose,
  onDownload,
  downloading,
  downloadError,
}: {
  task: Task;
  onClose: () => void;
  onDownload: (task: Task) => void;
  downloading: number | null;
  downloadError: string;
}) {
  const result = usePolling(`task-${task.id}`, (signal) =>
    api.task(task.id, signal),
  );
  const current = result.data || task;
  const catalog = useTaskCatalog();
  const definition = catalog.byId[current.type];
  const stage =
    current.status === "COMPLETE"
      ? 3
      : current.status === "IN_PROGRESS"
        ? 2
        : current.status === "INITIAL"
          ? 0
          : 1;
  return (
    <Modal
      title={`Task #${String(current.id).padStart(4, "0")}`}
      onClose={onClose}
    >
      <div className="modal-body">
        <div className="detail-file">
          <span className="file-icon">
            <TaskIcon task={current} size={23} />
          </span>
          <div>
            <h3>{current.filename}</h3>
            <p>
              {definition?.label || current.type} · {bytes(current.fileSize)}
            </p>
          </div>
          <Status status={current.status} />
        </div>
        {result.error && (
          <ErrorNotice message={result.error} retry={result.refresh} />
        )}
        {downloadError && <ErrorNotice message={downloadError} />}
        <ol
          className={`task-pipeline ${current.status === "FAILED" ? "failed" : ""}`}
        >
          {["Submitted", "Queued", "Processing", "Completed"].map(
            (label, i) => (
              <li className={i <= stage ? "reached" : ""} key={label}>
                <span>
                  {i < stage || current.status === "COMPLETE" ? (
                    <Check size={14} />
                  ) : (
                    i + 1
                  )}
                </span>
                <small>{label}</small>
              </li>
            ),
          )}
        </ol>
        {current.status === "FAILED" && (
          <ErrorNotice message="Processing failed. Check the source file and submit a new task. Detailed errors are available in the executor logs." />
        )}
        <dl className="detail-grid">
          <div>
            <dt>Submitted by</dt>
            <dd>{current.owner}</dd>
          </div>
          <div>
            <dt>Executor</dt>
            <dd className="mono">
              {current.executorId || "Waiting for assignment"}
            </dd>
          </div>
          <div>
            <dt>Submitted</dt>
            <dd>{date(current.createdAt, true)}</dd>
          </div>
          <div>
            <dt>Last status update</dt>
            <dd>{date(current.updatedAt, true)}</dd>
          </div>
          <div>
            <dt>Output format</dt>
            <dd>{definition?.output || "Operation details unavailable"}</dd>
          </div>
          <div>
            <dt>Current state</dt>
            <dd>{statusLabels[current.status]}</dd>
          </div>
        </dl>
        {!!Object.keys(current.options || {}).length && (
          <dl className="detail-grid">
            {Object.entries(current.options || {}).map(([name, value]) => (
              <div key={name}><dt>{definition?.options?.find(field => field.name === name)?.label || name}</dt><dd>{value}</dd></div>
            ))}
          </dl>
        )}
        {current.textPreviewAvailable && <TextPreview taskId={current.id} />}
        {(current.artifacts || []).filter(artifact => artifact.path !== "result").map(artifact => (
          <ArtifactDownload key={artifact.path} task={current} artifact={artifact} />
        ))}
        {!current.resultAvailable && current.status !== "FAILED" && (
          <div className="subtle-note">
            <LoaderCircle size={16} className="spin" />
            <span>
              {current.status === "INITIAL"
                ? "Waiting for the coordinator to assign an executor."
                : "This view refreshes automatically. Your download will appear when the task is complete."}
            </span>
          </div>
        )}
      </div>
      <div className="modal-footer">
        <button className="button secondary" onClick={onClose}>
          Close
        </button>
        <button
          className="button primary"
          onClick={() => onDownload(current)}
          disabled={!current.resultAvailable || downloading === current.id}
        >
          {downloading === current.id ? (
            <LoaderCircle size={16} className="spin" />
          ) : (
            <ArrowDownToLine size={16} />
          )}
          {current.artifacts?.find(artifact => artifact.path === "result")?.label || "Download result"}
        </button>
      </div>
    </Modal>
  );
}

function TextPreview({ taskId }: { taskId: number }) {
  const [preview, setPreview] = useState<{text: string; truncated: boolean}>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setPreview(undefined);
    setError("");
    api.preview(taskId, controller.signal).then(setPreview).catch(error => {
      if (!controller.signal.aborted) setError(error instanceof Error ? error.message : "Could not load text.");
    });
    return () => controller.abort();
  }, [taskId, attempt]);
  return <section className="text-preview" aria-label="Transcript">
    <h3>Transcript</h3>
    {error ? <ErrorNotice message={error} retry={() => setAttempt(value => value + 1)} /> : !preview ? <Loading text="Loading transcript…" /> : <>
      <pre>{preview.text || "No speech was detected."}</pre>
      {preview.truncated && <p>Preview shortened. Download the text file to read the full transcript.</p>}
    </>}
  </section>;
}

function ArtifactDownload({task, artifact}: {task: Task; artifact: {path: string; label: string}}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function download() {
    setBusy(true);
    setError("");
    try { await api.download(task, artifact.path); }
    catch (error) { setError(error instanceof Error ? error.message : "Download failed."); }
    finally { setBusy(false); }
  }
  return <div className="artifact-download">
    <button className="button secondary" disabled={busy} onClick={download}>
      {busy ? <LoaderCircle size={16} className="spin" /> : <ArrowDownToLine size={16} />}{artifact.label}
    </button>
    {error && <ErrorNotice message={error} />}
  </div>;
}

export function HelpDialog({ onClose }: { onClose: () => void }) {
  const catalog = useTaskCatalog();
  return (
    <Modal title="A little help with Krite" onClose={onClose}>
      <div className="modal-body help-content">
        <p>
          Krite distributes your work across available executors. Submit a file,
          follow its progress, and download the result.
        </p>
        <h3>Supported operations</h3>
        {catalog.loading && <Loading text="Loading operations…" />}
        {catalog.error && (
          <ErrorNotice message={catalog.error} retry={catalog.refresh} />
        )}
        {!catalog.loading && !catalog.error && !catalog.types.length && (
          <p>No operations are currently available.</p>
        )}
        {catalog.types.map((operation) => (
          <p key={operation.id}>
            <strong>{operation.label}</strong> — {operation.description}{" "}
            {operation.helpText}
          </p>
        ))}
        <h3>Following your tasks</h3>
        <p>
          Pending tasks wait for assignment, queued tasks wait for an executor,
          and running tasks are being processed. Open a task to see its current
          state and download completed results.
        </p>
        <h3>Executor status</h3>
        <p>
          Administrators can see CPU usage, memory usage, and assigned task
          counts. Executors are marked offline after 10 seconds without a
          heartbeat. The dashboard refreshes every 5 seconds; offline metrics
          show the last report.
        </p>
        <h3>Your workspace</h3>
        <p>
          Regular accounts see only their own tasks. Administrators can inspect
          tasks across the workspace and monitor the executor fleet.
        </p>
      </div>
      <div className="modal-footer">
        <button className="button primary" onClick={onClose}>
          Got it
        </button>
      </div>
    </Modal>
  );
}
