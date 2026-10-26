# Krite console

A React 19 + TypeScript frontend built with Vite. Neutral surfaces, muted green
accents, responsive layouts, and real API data. No demo data is used by the app;
fixtures live only in the browser tests.

## Start locally

Requirements: Node.js 22.12+ (Node 24 works), npm, and JDK 21 for the backend.

Run the three backend services from the repository root, each in its own terminal:

```bash
./gradlew :coordinator:bootRun
./gradlew :executor:bootRun
./gradlew :api:bootRun
```

PostgreSQL (`krite` database), MongoDB (`executor_logs`), and RustFS (`krite` bucket)
must be available at the addresses in each service's `application.properties`.

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. Log in with an existing account. On a new database the
backend creates the development administrator `admin` / `1234`. Configure the
initial administrator using `krite.admin.username`
and `krite.admin.password` in the API's `application.properties` (environment overrides:
`KRITE_ADMIN_USERNAME` and `KRITE_ADMIN_PASSWORD`). Existing accounts are not reset.
Register through the sign-in screen to create a regular account. The backend's default admin
credentials and JWT signing configuration are for local development; configure
appropriate credentials before exposing the application outside your machine.

The frontend proxies `/auth`, `/api`, and `/admin` to `http://127.0.0.1:8080`.
To use another API server, set the server-side environment variable:

```bash
API_TARGET=http://127.0.0.1:8081 npm run dev
```

Authentication uses a JWT kept in the current tab's session storage. Expired or
invalid sessions return to sign-in. Regular users cannot fetch other users' tasks
or executor telemetry, even by calling the API directly.

## Features

- Admin overview: task counts, hourly submissions, fleet availability, CPU/memory
  usage, heartbeat state, and recently submitted tasks.
- Executor directory: online/offline filters and search by ID or hostname.
- User overview: personal task counts, activity, and operation shortcuts.
- Task history: filename search, status/type filters, pagination, and details.
- Submission dialog: drag-and-drop or file picker, type/size checks, image preview,
  upload progress, and immediate access to the created task.
- Authenticated downloads for completed task artifacts, including PNG thumbnails, `.zst` archives, transcript TXT files, and subtitled MP4 videos.
- Empty, loading, stale, offline, error, retry, and expired-session states.
- Responsive navigation and tables, keyboard-accessible dialogs, reduced-motion
  support, and bundled fonts.

CPU and memory values are system-level readings reported by each executor.
Running/queued counts are database task states, not thread-pool occupancy.
Executors are offline after 10 seconds without a heartbeat; polling runs every
5 seconds. A disconnected worker's assigned tasks may need operational recovery;
this console does not implement task cancellation or automatic retries.

## Backend-driven operation catalog

`src/TaskCatalog.tsx` loads `GET /api/task/types` after sign-in and shares its
metadata across the workspace. The UI has no operation identifier list or
operation-specific descriptions, file restrictions, or filename conventions.
It refreshes the catalog every minute and on manual refresh. Loading, failure,
retry, and empty states are handled without showing invented operations.

Descriptors provide `id`, `label`, `description`, `output`, `acceptedMimeTypes`,
`inputDescription`, `helpText`, `icon`, and `maxFileSizeBytes`. Labels, help text,
shortcuts, filters, file acceptance, and size validation come from these fields.
The API enforces upload constraints as well. The generic icon renderer recognizes
`image` and `archive`; any other value gets a file icon. Downloads use the server's
`Content-Disposition` filename, including UTF-8 names.

New file-based backend operations appear automatically. An old task whose type
is absent from the catalog still displays its identifier and can be downloaded.
Restart the updated API when upgrading from the old string-array catalog endpoint.

## Build

```bash
npm run build
npm run preview
```

`build` runs strict TypeScript checks and writes static assets to `dist/`.
The preview is at http://localhost:4173 and uses the same API proxy. For deployment,
serve `dist/` with a static server and reverse-proxy `/api/`, `/auth/`, and `/admin/`
to the API. Vite's development/preview proxy is not included in the static build.
Navigation uses URL hashes, so no SPA route rewrites are needed.

## Browser tests

```bash
npx playwright install chromium
npm test
```

On NixOS, use the Nix-provided Chromium instead of Playwright's downloaded binary:

```bash
nix-shell -p chromium --run 'CHROMIUM_PATH="$(command -v chromium)" npm test'
```

Tests start Vite automatically and use intercepted API fixtures. They cover admin
metrics, user uploads and validation, filtering, downloads, mobile overflow and
navigation, session expiration, unavailable API responses, and a previously unknown
operation including server-driven validation and UTF-8 result filenames. The real backend
has separate authorization and compression regression tests documented in the root
README. Screenshots and failure traces are written to `test-results/` (gitignored).

## Troubleshooting

- **Java 21 not found:** on the configured NixOS machine, run
  `export JAVA_HOME=/run/current-system/sw/lib/openjdk` before Gradle; select the
  same JDK for IntelliJ's Gradle JVM.
- **Cannot reach the API:** start `:api:bootRun` and check `API_TARGET`.
- **Files will not upload:** check RustFS is healthy and its `krite` bucket exists.
  From the repository root, `docker compose ps -a` shows the server and bucket initializer status.
- **Tasks stay pending:** run the coordinator and at least one executor. Give each
  executor a unique `executor.id` when running multiple instances.
- **No executors shown:** restart the updated coordinator and executor so they
  publish telemetry to the shared database.


Transcription uses the same catalog-driven form. Each operation may supply `options`
with `name`, `label`, `kind` (`select` or `boolean`), `choices`, and `defaultValue`.
The form sends a JSON string in the multipart `options` field, with string values.
Model choices and the presence of the subtitle checkbox come exclusively from the
backend. Task details use `textPreviewAvailable` to load a transcript preview and
`artifacts` to show download buttons; no task identifiers or model names are built
into the frontend. See the root README for executor dependencies, configuration,
and the existing-database migration.
