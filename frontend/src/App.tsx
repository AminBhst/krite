import { useEffect, useState, type FormEvent } from "react";
import {
  Activity,
  ArrowRight,
  ArrowUpRight,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Clock3,
  Cpu,
  Layers3,
  LayoutDashboard,
  ListTodo,
  LoaderCircle,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Search,
  Server,
  ShieldCheck,
  X,
} from "lucide-react";
import { api, hasSession, setToken } from "./api";
import { TaskCatalogProvider, useTaskCatalog } from "./TaskCatalog";
import {
  bytes,
  Empty,
  ErrorNotice,
  ExecutorCard,
  Loading,
  Logo,
  number,
  TaskTable,
  TaskIcon,
} from "./components";
import { useDebounce, usePolling } from "./hooks";
import { HelpDialog, NewTaskDialog, TaskDetailDialog } from "./TaskDialogs";
import {
  queueStatuses,
  statusLabels,
  type Account,
  type Executor,
  type Summary,
  type Task,
  type TaskStatus,
  type TaskType,
} from "./types";

type View = "overview" | "tasks" | "executors";
type Modal =
  | { kind: "new"; type?: TaskType }
  | { kind: "detail"; task: Task }
  | { kind: "help" }
  | null;
const readView = (): View =>
  ["tasks", "executors"].includes(location.hash.slice(1))
    ? (location.hash.slice(1) as View)
    : "overview";

export default function App() {
  const [account, setAccount] = useState<Account | null>(null);
  const [checking, setChecking] = useState(hasSession);
  const [authMessage, setAuthMessage] = useState("");
  useEffect(() => {
    let active = true;
    if (hasSession())
      api
        .me()
        .then((user) => {
          if (active) setAccount(user);
        })
        .catch((error) => {
          if (active) {
            setToken(null);
            setAuthMessage(error.message);
          }
        })
        .finally(() => {
          if (active) setChecking(false);
        });
    const expired = () => {
      setToken(null);
      setAccount(null);
      setAuthMessage("Your session has expired. Please sign in again.");
    };
    window.addEventListener("krite:session-expired", expired);
    return () => {
      active = false;
      window.removeEventListener("krite:session-expired", expired);
    };
  }, []);
  if (checking)
    return (
      <div className="startup">
        <Logo />
        <Loading />
      </div>
    );
  if (!account)
    return <AuthScreen message={authMessage} onLogin={setAccount} />;
  return (
    <TaskCatalogProvider>
      <Workspace
        account={account}
        onLogout={() => {
          setToken(null);
          setAccount(null);
          setAuthMessage("");
          location.hash = "";
        }}
      />
    </TaskCatalogProvider>
  );
}

function AuthScreen({
  message,
  onLogin,
}: {
  message: string;
  onLogin: (account: Account) => void;
}) {
  const [registering, setRegistering] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(message);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (registering) await api.register(username.trim(), password);
      const { token } = await api.login(username.trim(), password);
      setToken(token);
      onLogin(await api.me());
    } catch (error) {
      setToken(null);
      setError(
        error instanceof Error
          ? error.message
          : "Could not connect. Please try again.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Logo />
        <div className="auth-story-content">
          <span className="eyebrow">A LITTLE ORDER. A LOT OF WORK.</span>
          <h1>
            Your tasks.
            <br />
            Working together.
          </h1>
          <p>
            A clear view of your work, from the first upload to the finished
            file.
          </p>
          <div className="flow-illustration" aria-hidden="true">
            <div className="flow-source">
              <Layers3 size={27} />
              <span>Task queue</span>
            </div>
            <div className="flow-lines">
              <span />
              <span />
              <span />
            </div>
            <div className="flow-nodes">
              {[1, 2, 3].map((i) => (
                <span key={i}>
                  <Server size={19} />
                  <i />
                  <i />
                  <i />
                </span>
              ))}
            </div>
          </div>
          <div className="story-points">
            <span>
              <Check size={16} />
              Distributed processing
            </span>
            <span>
              <Check size={16} />
              One place to keep track
            </span>
          </div>
        </div>
        <span className="auth-footer">Krite / Distributed task executor</span>
      </section>
      <section className="auth-form-panel">
        <div className="auth-mobile-brand">
          <Logo />
        </div>
        <form className="auth-form" onSubmit={submit}>
          <span className="eyebrow">YOUR WORKSPACE</span>
          <h2>{registering ? "Make yourself at home." : "Welcome back."}</h2>
          <p>
            {registering
              ? "Create an account to start submitting tasks."
              : "Sign in to see what’s happening with your work."}
          </p>
          <label>
            Username
            <input
              autoFocus
              autoComplete="username"
              required
              minLength={registering ? 3 : 1}
              maxLength={40}
              pattern={registering ? "[a-zA-Z0-9._\\-]+" : undefined}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="Your username"
              disabled={busy}
            />
          </label>
          <label>
            Password
            <input
              autoComplete={registering ? "new-password" : "current-password"}
              type="password"
              required
              minLength={registering ? 8 : 1}
              maxLength={72}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={
                registering ? "At least 8 characters" : "Your password"
              }
              disabled={busy}
            />
          </label>
          {error && <ErrorNotice message={error} />}
          <button className="button primary auth-submit" disabled={busy}>
            {busy ? <LoaderCircle size={17} className="spin" /> : null}
            {busy ? "Connecting…" : registering ? "Create account" : "Sign in"}
            {!busy && <ArrowRight size={17} />}
          </button>
          <div className="auth-switch">
            {registering ? "Already have an account?" : "New to Krite?"}{" "}
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setRegistering(!registering);
                setError("");
              }}
            >
              {registering ? "Sign in" : "Create an account"}
            </button>
          </div>
          <div className="auth-role-note">
            <ShieldCheck size={17} />
            Your account determines your workspace access.
          </div>
        </form>
        <span className="auth-panel-footer">
          Less waiting. More getting things done.
        </span>
      </section>
    </main>
  );
}

function Workspace({
  account,
  onLogout,
}: {
  account: Account;
  onLogout: () => void;
}) {
  const admin = account.role === "ADMIN";
  const catalog = useTaskCatalog();
  const [view, setView] = useState<View>(readView);
  const [mobileNav, setMobileNav] = useState(false);
  const [modal, setModal] = useState<Modal>(null);
  const [revision, setRevision] = useState(0);
  const [toast, setToast] = useState("");
  const [downloadError, setDownloadError] = useState("");
  const [downloading, setDownloading] = useState<number | null>(null);
  const summary = usePolling(`summary-${revision}`, api.summary);
  const fleet = usePolling(`fleet-${admin}-${revision}`, (signal) =>
    admin ? api.executors(signal) : Promise.resolve([]),
  );
  useEffect(() => {
    const changed = () => {
      setView(readView());
      setMobileNav(false);
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  useEffect(() => {
    if (!admin && view === "executors") location.hash = "overview";
  }, [admin, view]);
  useEffect(() => {
    if (toast) {
      const timeout = setTimeout(() => setToast(""), 6000);
      return () => clearTimeout(timeout);
    }
  }, [toast]);
  const navigate = (target: View) => {
    location.hash = target;
    setView(target);
    setMobileNav(false);
  };
  const create = (type?: TaskType) => setModal({ kind: "new", type });
  async function download(task: Task) {
    setDownloading(task.id);
    setDownloadError("");
    try {
      await api.download(task);
      setToast("Your download is ready.");
    } catch (error) {
      setDownloadError(
        error instanceof Error ? error.message : "Download failed.",
      );
    } finally {
      setDownloading(null);
    }
  }
  const refresh = () => {
    summary.refresh();
    fleet.refresh();
    catalog.refresh();
    setRevision((value) => value + 1);
  };
  const fleetOnline = (fleet.data || []).filter((e) => e.online).length;
  const taskProps = {
    admin,
    onDetail: (task: Task) => setModal({ kind: "detail", task }),
    onDownload: download,
    downloading,
  };
  return (
    <div className="app-shell">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        Skip to content
      </a>
      {mobileNav && (
        <button
          className="sidebar-scrim"
          aria-label="Close navigation"
          onClick={() => setMobileNav(false)}
        />
      )}
      <aside className={`sidebar ${mobileNav ? "open" : ""}`}>
        <div className="sidebar-brand">
          <Logo />
          <button
            className="icon-button mobile-close"
            aria-label="Close navigation"
            onClick={() => setMobileNav(false)}
          >
            <X size={20} />
          </button>
        </div>
        <div className="workspace-select">
          <span className="workspace-icon">
            <Layers3 size={18} />
          </span>
          <span>
            <strong>Krite workspace</strong>
            <small>{admin ? "Administrator" : "Personal workspace"}</small>
          </span>
          <span className="workspace-tag">{admin ? "A" : "U"}</span>
        </div>
        <div className="nav-label">WORKSPACE</div>
        <nav aria-label="Main navigation">
          <a
            href="#overview"
            className={view === "overview" ? "active" : ""}
            aria-current={view === "overview" ? "page" : undefined}
          >
            <LayoutDashboard size={18} />
            Overview
          </a>
          <a
            href="#tasks"
            className={view === "tasks" ? "active" : ""}
            aria-current={view === "tasks" ? "page" : undefined}
          >
            <ListTodo size={18} />
            {admin ? "All tasks" : "My tasks"}
          </a>
          {admin && (
            <a
              href="#executors"
              className={view === "executors" ? "active" : ""}
              aria-current={view === "executors" ? "page" : undefined}
            >
              <Server size={18} />
              Executors
              {fleet.data && (
                <span className="nav-count">{fleet.data.length}</span>
              )}
            </a>
          )}
        </nav>
        <div className="nav-label task-nav-label">QUICK CREATE</div>
        <nav aria-label="Create a task">
          {catalog.types.map((operation) => (
            <button key={operation.id} onClick={() => create(operation.id)}>
              <TaskIcon task={{ type: operation.id }} size={18} />
              {operation.label}
              <Plus size={14} className="nav-plus" />
            </button>
          ))}
          {catalog.loading && (
            <span className="muted">Loading operations…</span>
          )}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="small-mark">
              <Activity size={16} />
            </span>
            <strong>A place for every task.</strong>
            <p>Submit, process, and keep moving.</p>
          </div>
          <button
            className="help-link"
            onClick={() => setModal({ kind: "help" })}
          >
            <CircleHelp size={17} />
            Help & documentation
            <ArrowUpRight size={14} />
          </button>
          <div className="account">
            <span className="avatar">
              {account.username.slice(0, 2).toUpperCase()}
            </span>
            <div>
              <strong>{account.username}</strong>
              <small>{admin ? "Administrator" : "Member"}</small>
            </div>
            <button
              className="icon-button"
              aria-label="Sign out"
              onClick={onLogout}
            >
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div>
            <button
              className="icon-button mobile-menu"
              aria-label="Open navigation"
              onClick={() => setMobileNav(true)}
            >
              <Menu size={21} />
            </button>
            <span className="breadcrumb">
              Workspace <span>/</span>{" "}
              <strong>
                {view === "overview"
                  ? "Overview"
                  : view === "executors"
                    ? "Executors"
                    : admin
                      ? "All tasks"
                      : "My tasks"}
              </strong>
            </span>
          </div>
          <div className="topbar-right">
            <span
              className={`sync-status ${summary.error ? "disconnected" : ""}`}
            >
              <span className="status-dot" />
              {summary.error
                ? "Connection interrupted"
                : summary.loading
                  ? "Connecting"
                  : "Live updates"}
            </span>
            <span className="topbar-divider" />
            <span className="topbar-date">
              {new Date().toLocaleDateString(undefined, {
                month: "short",
                day: "numeric",
                year: "numeric",
              })}
            </span>
          </div>
        </header>
        <main id="main-content" tabIndex={-1} className="main-content">
          <div className="page-heading">
            <div>
              <div className="eyebrow">
                {admin ? "WORKSPACE MONITOR" : "YOUR WORK, IN VIEW"}
              </div>
              <h1>
                {view === "overview"
                  ? "Overview"
                  : view === "executors"
                    ? "Executors"
                    : admin
                      ? "All tasks"
                      : "My tasks"}
                <span className="heading-dot">.</span>
              </h1>
              <p>
                {view === "overview"
                  ? admin
                    ? "A clear view of your tasks and the machines behind them."
                    : "Everything you’ve submitted, from start to finish."
                  : view === "executors"
                    ? "Keep an eye on capacity, load, and the health of your fleet."
                    : admin
                      ? "Track every task across your workspace."
                      : "Follow your tasks and download finished results."}
              </p>
            </div>
            <div className="page-actions">
              <button
                className="button secondary square-button"
                aria-label="Refresh data"
                onClick={refresh}
              >
                <RefreshCw size={17} />
              </button>
              <button className="button primary" onClick={() => create()}>
                <Plus size={17} />
                New task
              </button>
            </div>
          </div>
          {catalog.error && (
            <ErrorNotice
              message={`Could not load operation details. ${catalog.error}`}
              retry={catalog.refresh}
            />
          )}
          {summary.error && (
            <ErrorNotice
              message={`${summary.error}${summary.data ? " Showing the last successful update." : ""}`}
              retry={summary.refresh}
            />
          )}
          {downloadError && <ErrorNotice message={downloadError} />}
          {view === "overview" && (
            <>
              <Metrics
                summary={summary.data}
                executors={fleet.data}
                admin={admin}
              />
              <div className="overview-middle">
                <ActivityChart summary={summary.data} />
                <div className="panel fleet-summary">
                  <div className="panel-heading">
                    <h2>
                      {admin ? "Fleet at a glance" : "Start something new"}
                    </h2>
                    <span className="section-marker">
                      {admin ? <Server size={17} /> : <Layers3 size={17} />}
                    </span>
                  </div>
                  {admin ? (
                    fleet.error ? (
                      <ErrorNotice
                        message={fleet.error}
                        retry={fleet.refresh}
                      />
                    ) : fleet.loading ? (
                      <Loading text="Checking executors…" />
                    ) : (
                      <>
                        <div className="fleet-number">
                          <span>{fleetOnline}</span>
                          <div>
                            <strong>executors online</strong>
                            <small>
                              of {fleet.data?.length || 0} registered
                            </small>
                          </div>
                          <span
                            className={`fleet-status-dot ${fleetOnline ? "" : "offline-dot"}`}
                          />
                        </div>
                        <div className="fleet-summary-metrics">
                          <div>
                            <Cpu size={16} />
                            <span>Average CPU</span>
                            <strong>
                              {fleetOnline
                                ? `${Math.round(((fleet.data || []).filter((e) => e.online).reduce((sum, e) => sum + e.cpuLoad, 0) / fleetOnline) * 100)}%`
                                : "—"}
                            </strong>
                          </div>
                          <div>
                            <Layers3 size={16} />
                            <span>Available memory</span>
                            <strong>
                              {fleetOnline
                                ? bytes(
                                    (fleet.data || [])
                                      .filter((e) => e.online)
                                      .reduce(
                                        (sum, e) => sum + e.memoryFree,
                                        0,
                                      ),
                                  )
                                : "—"}
                            </strong>
                          </div>
                        </div>
                        <button
                          className="panel-footer-link"
                          onClick={() => navigate("executors")}
                        >
                          View executors
                          <ArrowRight size={16} />
                        </button>
                      </>
                    )
                  ) : (
                    <div className="quick-create-list">
                      {catalog.types.map((operation) => (
                        <button
                          key={operation.id}
                          onClick={() => create(operation.id)}
                        >
                          <span className="file-icon">
                            <TaskIcon task={{ type: operation.id }} size={21} />
                          </span>
                          <span>
                            <strong>{operation.label}</strong>
                            <small>{operation.output}</small>
                          </span>
                          <ArrowRight size={17} />
                        </button>
                      ))}
                      {catalog.loading && (
                        <Loading text="Loading operations…" />
                      )}
                      {!catalog.loading &&
                        !catalog.error &&
                        !catalog.types.length && (
                          <p>No operations are currently available.</p>
                        )}
                      <p>Upload a file. We’ll take it from there.</p>
                    </div>
                  )}
                </div>
              </div>
              {admin && (
                <section className="executor-section">
                  <div className="section-heading">
                    <div>
                      <h2>
                        Executor health{" "}
                        <span className="inline-count">
                          {fleet.data?.length || 0}
                        </span>
                      </h2>
                      <p>Current resource usage across your fleet.</p>
                    </div>
                    <button
                      className="text-button"
                      onClick={() => navigate("executors")}
                    >
                      View all
                      <ArrowRight size={15} />
                    </button>
                  </div>
                  {fleet.error ? (
                    <ErrorNotice message={fleet.error} retry={fleet.refresh} />
                  ) : fleet.loading ? (
                    <Loading text="Loading executors…" />
                  ) : fleet.data?.length ? (
                    <div className="executor-grid">
                      {fleet.data.slice(0, 3).map((executor) => (
                        <ExecutorCard key={executor.id} executor={executor} />
                      ))}
                    </div>
                  ) : (
                    <div className="empty-inline">
                      <Server size={22} />
                      <div>
                        <strong>No executors registered yet</strong>
                        <p>
                          Start the coordinator and an executor to see live
                          resource usage here.
                        </p>
                      </div>
                    </div>
                  )}
                </section>
              )}
              <TasksPanel
                {...taskProps}
                revision={revision}
                compact
                onViewAll={() => navigate("tasks")}
                onCreate={create}
              />
            </>
          )}
          {view === "tasks" && (
            <TasksPanel {...taskProps} revision={revision} onCreate={create} />
          )}
          {view === "executors" && admin && (
            <ExecutorsView
              executors={fleet.data}
              error={fleet.error}
              loading={fleet.loading}
              refresh={fleet.refresh}
            />
          )}
          <footer className="page-footer">
            <span>
              Krite<span className="separator">/</span>Distributed task executor
            </span>
            <span>
              {summary.at
                ? `Last synced ${summary.at.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" })}`
                : "Waiting for first update"}
              <span className="separator">·</span>Refreshes every 5s
            </span>
          </footer>
        </main>
      </div>
      {modal?.kind === "new" && (
        <NewTaskDialog
          initialType={modal.type}
          onClose={() => setModal(null)}
          onCreated={(task) => {
            setModal({ kind: "detail", task });
            setRevision((value) => value + 1);
            setToast(`Task #${task.id} submitted successfully.`);
          }}
        />
      )}
      {modal?.kind === "detail" && (
        <TaskDetailDialog
          task={modal.task}
          onClose={() => setModal(null)}
          onDownload={download}
          downloading={downloading}
          downloadError={downloadError}
        />
      )}
      {modal?.kind === "help" && <HelpDialog onClose={() => setModal(null)} />}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toast}
          <button
            aria-label="Dismiss notification"
            onClick={() => setToast("")}
          >
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}

function Metrics({
  summary,
  executors,
  admin,
}: {
  summary?: Summary;
  executors?: Executor[];
  admin: boolean;
}) {
  const counts = summary?.counts;
  const queued = counts
    ? queueStatuses.reduce((sum, status) => sum + counts[status], 0)
    : 0;
  const total = counts
    ? Object.values(counts).reduce((sum, count) => sum + count, 0)
    : 0;
  const finished = counts ? counts.COMPLETE + counts.FAILED : 0;
  const cards = [
    {
      label: "Total tasks",
      value: number(total),
      icon: Layers3,
      note: admin ? "Across the workspace" : "Submitted by you",
      className: "",
    },
    {
      label: "In progress",
      value: number(counts?.IN_PROGRESS || 0),
      icon: Activity,
      note: `${number(queued)} waiting in queue`,
      className: "blue-stat",
    },
    {
      label: "Completed",
      value: number(counts?.COMPLETE || 0),
      icon: CheckCircle2,
      note: finished
        ? `${Math.round(((counts?.COMPLETE || 0) / finished) * 100)}% of finished tasks succeeded`
        : "Results ready to download",
      className: "green-stat",
    },
    admin
      ? {
          label: "Active executors",
          value: number(executors?.filter((e) => e.online).length || 0),
          icon: Server,
          note: executors?.length
            ? `${executors.length} registered in your fleet`
            : "Waiting for executors",
          className: "",
        }
      : {
          label: "Failed",
          value: number(counts?.FAILED || 0),
          icon: Clock3,
          note: "Tasks that need another look",
          className: "",
        },
  ];
  return (
    <div className="metrics-grid">
      {cards.map(({ icon: Icon, ...card }, index) => (
        <div className={`metric-card ${card.className}`} key={card.label}>
          <div className="metric-top">
            <span>{card.label}</span>
            <Icon size={17} />
          </div>
          <strong className="metric-number">
            {summary && (index !== 3 || !admin || executors) ? card.value : "—"}
          </strong>
          <span className="metric-note">{card.note}</span>
        </div>
      ))}
    </div>
  );
}

function ActivityChart({ summary }: { summary?: Summary }) {
  const [hover, setHover] = useState<number | null>(null);
  const buckets = summary?.submissions || [];
  const max = Math.max(4, ...buckets.map((bucket) => bucket.count));
  const total = buckets.reduce((sum, bucket) => sum + bucket.count, 0);
  return (
    <section className="panel activity-panel">
      <div className="panel-heading">
        <div>
          <h2>Task activity</h2>
          <p>Submissions over the last 24 hours</p>
        </div>
        <span className="period-label">
          <Clock3 size={14} />
          24 hours
        </span>
      </div>
      <div className="chart-headline">
        <strong>{summary ? number(total) : "—"}</strong>
        <span>tasks submitted</span>
        <span className="chart-legend">
          <i />
          Submitted
        </span>
      </div>
      {!summary ? (
        <Loading text="Loading activity…" />
      ) : (
        <div
          className="chart"
          aria-label={`${total} tasks submitted in the last 24 hours`}
        >
          <div className="chart-grid">
            {[max, Math.round(max / 2), 0].map((value, i) => (
              <div key={i}>
                <span>{value}</span>
                <i />
              </div>
            ))}
          </div>
          <div className="chart-bars">
            {buckets.map((bucket, index) => (
              <div className="chart-bar-slot" key={bucket.time}>
                <button
                  aria-label={`${new Date(bucket.time).toLocaleTimeString(undefined, { hour: "numeric" })}: ${bucket.count} tasks`}
                  onFocus={() => setHover(index)}
                  onBlur={() => setHover(null)}
                  onMouseEnter={() => setHover(index)}
                  onMouseLeave={() => setHover(null)}
                  style={{
                    height: `${Math.max(1.5, (bucket.count / max) * 100)}%`,
                  }}
                  className={bucket.count ? "" : "zero"}
                />
                {hover === index && (
                  <div className="chart-tooltip">
                    {new Date(bucket.time).toLocaleTimeString(undefined, {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                    <strong>{bucket.count} tasks</strong>
                  </div>
                )}
              </div>
            ))}
          </div>
          {!total && (
            <span className="chart-empty">
              Your task activity will appear here
            </span>
          )}
          <div className="chart-labels">
            {buckets
              .filter((_, i) => i % 6 === 0 || i === 23)
              .map((bucket) => (
                <span key={bucket.time}>
                  {new Date(bucket.time).toLocaleTimeString(undefined, {
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              ))}
          </div>
        </div>
      )}
    </section>
  );
}

type TaskProps = {
  admin: boolean;
  revision: number;
  onDetail: (task: Task) => void;
  onDownload: (task: Task) => void;
  downloading: number | null;
  compact?: boolean;
  onViewAll?: () => void;
  onCreate: () => void;
};
function TasksPanel(props: TaskProps) {
  const catalog = useTaskCatalog();
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounce(search);
  const size = props.compact ? 5 : 15;
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    search: debouncedSearch,
  });
  if (status) params.set("status", status);
  if (type) params.set("type", type);
  const result = usePolling(`tasks-${params}-${props.revision}`, (signal) =>
    api.tasks(params, signal),
  );
  const filtered = Boolean(search || status || type);
  return (
    <section className="panel tasks-panel">
      <div className="panel-heading">
        <div>
          <h2>
            {props.compact ? "Recent tasks" : "Task history"}
            {result.data && (
              <span className="inline-count">
                {number(result.data.totalElements)}
              </span>
            )}
          </h2>
          <p>
            {props.compact
              ? "The latest work moving through your workspace."
              : "Search, filter, and check the details of your work."}
          </p>
        </div>
        {props.compact && (
          <button className="text-button" onClick={props.onViewAll}>
            View all tasks
            <ArrowRight size={15} />
          </button>
        )}
      </div>
      {!props.compact && (
        <div className="table-toolbar">
          <label className="search-field">
            <Search size={17} />
            <input
              aria-label="Search tasks"
              placeholder="Search by filename…"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
            />
            {search && (
              <button
                aria-label="Clear search"
                onClick={() => {
                  setSearch("");
                  setPage(0);
                }}
              >
                <X size={15} />
              </button>
            )}
          </label>
          <div className="table-filters">
            <label className="select-wrap">
              <select
                aria-label="Filter by status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value);
                  setPage(0);
                }}
              >
                <option value="">All statuses</option>
                {(Object.keys(statusLabels) as TaskStatus[]).map((status) => (
                  <option key={status} value={status}>
                    {statusLabels[status]}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} />
            </label>
            <label className="select-wrap">
              <select
                aria-label="Filter by operation"
                value={type}
                onChange={(event) => {
                  setType(event.target.value);
                  setPage(0);
                }}
              >
                <option value="">All operations</option>
                {catalog.types.map((operation) => (
                  <option key={operation.id} value={operation.id}>
                    {operation.label}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} />
            </label>
          </div>
        </div>
      )}
      {result.error && (
        <div className="panel-error">
          <ErrorNotice
            message={`${result.error}${result.data ? " These results may be out of date." : ""}`}
            retry={result.refresh}
          />
        </div>
      )}
      {result.loading ? (
        <Loading text="Loading tasks…" />
      ) : result.data?.content.length ? (
        <TaskTable {...props} tasks={result.data.content} />
      ) : !result.error ? (
        <Empty
          title={filtered ? "No matching tasks" : "Your first task starts here"}
          action={
            <button
              className="button secondary"
              onClick={
                filtered
                  ? () => {
                      setSearch("");
                      setStatus("");
                      setType("");
                      setPage(0);
                    }
                  : props.onCreate
              }
            >
              {filtered ? (
                "Clear filters"
              ) : (
                <>
                  <Plus size={16} />
                  Create a task
                </>
              )}
            </button>
          }
        >
          {filtered
            ? "Try a different filename, status, or operation."
            : "Choose an operation and upload a file. We’ll keep track of it here."}
        </Empty>
      ) : null}
      {!props.compact && result.data && result.data.totalElements > 0 && (
        <div className="pagination">
          <span>
            Showing {page * size + 1}–
            {Math.min((page + 1) * size, result.data.totalElements)} of{" "}
            {number(result.data.totalElements)} tasks
          </span>
          <div>
            <span>
              Page {page + 1} of {result.data.totalPages}
            </span>
            <button
              className="icon-button"
              aria-label="Previous page"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft size={18} />
            </button>
            <button
              className="icon-button"
              aria-label="Next page"
              disabled={page + 1 >= result.data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function ExecutorsView({
  executors,
  error,
  loading,
  refresh,
}: {
  executors?: Executor[];
  error?: string;
  loading: boolean;
  refresh: () => void;
}) {
  const [filter, setFilter] = useState<"all" | "online" | "offline">("all");
  const [search, setSearch] = useState("");
  const visible = (executors || []).filter(
    (e) =>
      (filter === "all" || (filter === "online" ? e.online : !e.online)) &&
      `${e.id} ${e.hostname}`.toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <section>
      <div className="executor-toolbar">
        <div
          className="segmented-control"
          role="group"
          aria-label="Executor status"
        >
          {(["all", "online", "offline"] as const).map((value) => (
            <button
              key={value}
              className={filter === value ? "selected" : ""}
              aria-pressed={filter === value}
              onClick={() => setFilter(value)}
            >
              {value === "all"
                ? "All executors"
                : value === "online"
                  ? "Online"
                  : "Offline"}
              <span>
                {
                  (executors || []).filter(
                    (e) =>
                      value === "all" ||
                      (value === "online" ? e.online : !e.online),
                  ).length
                }
              </span>
            </button>
          ))}
        </div>
        <label className="search-field">
          <Search size={17} />
          <input
            aria-label="Search executors"
            placeholder="Find an executor…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </div>
      {error && (
        <ErrorNotice
          message={`${error}${executors ? " Showing the last successful update." : ""}`}
          retry={refresh}
        />
      )}
      {loading ? (
        <Loading text="Loading executors…" />
      ) : visible.length ? (
        <div className="executor-grid full">
          {visible.map((executor) => (
            <ExecutorCard executor={executor} key={executor.id} />
          ))}
        </div>
      ) : (
        !error && (
          <div className="panel">
            <Empty
              title={
                executors?.length
                  ? "No matching executors"
                  : "Waiting for your first executor"
              }
            >
              {executors?.length
                ? "Try another name or status filter."
                : "Start the coordinator, then start an executor with a unique executor.id. It will appear here after registration."}
            </Empty>
          </div>
        )
      )}
      <div className="monitor-note">
        <Activity size={17} />
        <p>
          Metrics refresh every 5 seconds. An executor is marked offline after
          10 seconds without a heartbeat. Offline metrics reflect its last
          report.
        </p>
      </div>
    </section>
  );
}
