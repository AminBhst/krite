# Krite: Distributed Task Executor

Krite is a distributed, scalable task-processing system consisting of a Coordinator and multiple Executors.
Clients send JWT-authenticated requests to the api module, upload task data, and the coordinator assigns the work
across available executors based on the executor CPU specs, load, and available memory.

It uses gRPC for internal and REST API for client level communications, RustFS object storage, PostgreSQL and MongoDB.

This system is ideal for CPU-heavy, IO-heavy, or parallelizable workloads, and can easily be extended to support more
task types.

## 🧱 Architecture Overview

Krite has three backend services, a shared `common` library, and a React console in `frontend/`.

### API

- Provides HTTP REST API for clients
- Provides username/password registration and JWT login
- Serves as the entry point for creating tasks

### Coordinator

- Acts as the load-balancer for executors
- Dispatches tasks to executors via gRPC
- Collects task results and statuses

### Executors

- Connect to coordinator via gRPC
- Poll for tasks
- Execute assigned jobs (thumbnail generation, file compression etc.)
- Push status updates back to coordinator

## 🚀 Getting Started

### Local object storage

Start RustFS and create the `krite` bucket before running the application:

```bash
docker compose up -d
docker compose ps -a
```

The S3 API is available at `http://localhost:9000` and the RustFS Console at
`http://localhost:9001`. For local development, both the server and application use:

```text
RUSTFS_ACCESS_KEY=rustfsadmin
RUSTFS_SECRET_KEY=fc011b5e757cf26aaa5d83a303ebba263527fdc4f4a649a9febbc08bda34d19a
```

Override `RUSTFS_URL`, `RUSTFS_REGION`, `RUSTFS_ACCESS_KEY`,
`RUSTFS_SECRET_KEY`, or `RUSTFS_BUCKET` when connecting to another deployment.
`docker compose down` stops local RustFS while retaining its named data volume.

If the existing MinIO `krite` bucket contains objects, keep MinIO running on a
different port and copy the bucket before switching the application endpoint.
Pause application writes for the final mirror and comparison:

```bash
rc alias set minio http://localhost:9100 <minio-access-key> <minio-secret-key>
rc alias set rustfs http://localhost:9000 rustfsadmin fc011b5e757cf26aaa5d83a303ebba263527fdc4f4a649a9febbc08bda34d19a
rc mirror minio/krite rustfs/krite
rc diff --recursive minio/krite rustfs/krite
```

Do not remove the MinIO data until `rc diff` reports no differences and the
application has successfully downloaded existing task artifacts from RustFS.
To roll back during that window, set `RUSTFS_URL` and the `RUSTFS_ACCESS_KEY` /
`RUSTFS_SECRET_KEY` client values to the retained MinIO endpoint and credentials,
then restart the application modules.

### 1. Run the API

```bash
./gradlew :api:bootRun
```

### 2. Configure the Coordinator

edit the `application.properties` and set proper address and ports for gRPC, PostgreSQL and other configurations.

### 3. Run the Coordinator

```bash
./gradlew :coordinator:bootRun
```

### 4. Configure executors

edit the `application.properties` and set proper address and ports for gRPC to enable communication with the
coordinator.

Make sure to set a unique executor.id for each executor.

### 5. Run the executors

```bash
./gradlew :executor:bootRun
```

# 🔐 Authentication

Krite uses username/password authentication.
Clients must first log in to receive a JWT token and then send it in the Authorization header.

## 1. Register

### POST /auth/register

Request Body:

```json
{
  "username": "newuser",
  "password": "abcd1234"
}
```

## 2. Login

### POST /auth/login

Request Body:

```json
{
  "username": "newuser",
  "password": "abcd1234"
}

```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
}
```

# 📤 Creating Tasks

To create a task, send a multipart/form-data POST request to:

### POST /api/task/{taskType}

Example request (thumbnail generation):

```bash
POST /api/task/GENERATE_THUMBNAIL
Authorization: Bearer <token>
Content-Type: multipart/form-data
```


## Frontend console

The React + TypeScript console lives in [`frontend/`](frontend/README.md).
Administrators see all tasks and live executor CPU, memory, heartbeat status, and
assigned task counts. Regular accounts can create tasks, track only their own
history, and download completed results. Authorization is enforced by the API.

With the databases, RustFS, API, coordinator, and executor running, start the UI:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. The existing local development admin account is
`admin` / `1234` (unless its password has been changed). Use **Create an account**
for a regular user; registration always creates the `USER` role.

Configure the initial admin account in `api/src/main/resources/application.properties`:

```properties
krite.admin.username=${KRITE_ADMIN_USERNAME:admin}
krite.admin.password=${KRITE_ADMIN_PASSWORD:1234}
```

Set the values directly or supply `KRITE_ADMIN_USERNAME` and `KRITE_ADMIN_PASSWORD`
environment variables. The account is created at API startup only if the configured
username is absent. Existing passwords and roles are preserved; changing the
properties does not reset an existing account. Passwords are hashed before storage
and are not logged.

Supported operations:

- **Generate thumbnail:** JPEG, PNG, GIF, or BMP → PNG, with a 300 px longest edge.
- **Transcribe audio or video:** Whisper transcript → TXT preview/download, with optional permanently subtitled MP4 output.
- **Compress file:** any file → lossless Zstandard `.zst` archive. This is not
  JPEG/WebP optimization. Extract with `zstd -d filename.zst` or a compatible tool.

Uploads are limited to 100 MiB. The local Compose stack creates the `krite` RustFS bucket before submitting
files. The coordinator persists executor heartbeat metrics into PostgreSQL; the
console polls every 5 seconds and treats a heartbeat older than 10 seconds as
offline. Offline nodes show their last reported CPU and memory values. Activity
charts show hourly submissions over the last 24 hours; resource usage is a current
snapshot, not a historical time series.

### Console API

All routes except `/auth/*` require `Authorization: Bearer <token>`.

| Route | Purpose |
| --- | --- |
| `GET /api/me` | Current username and role |
| `GET /api/dashboard` | Role-scoped task totals and hourly submission counts |
| `GET /api/task/types` | Operation catalog: identifiers, labels, descriptions, input rules, output details, icons, and upload limits |
| `GET /api/task` | Paginated tasks; optional `page`, `size`, `status`, `type`, `search` (filename) |
| `POST /api/task/{taskType}` | Multipart upload in field `file`; returns the created task |
| `GET /api/task/{id}` | Task details, limited to owner or admin |
| `GET /api/task/{id}/result` | Authenticated result download, limited to owner or admin |
| `GET /admin/executors` | Admin-only executor telemetry |

### Task catalog

`GET /api/task/types` returns an array of descriptors, for example:

```json
{
  "id": "GENERATE_THUMBNAIL",
  "label": "Generate thumbnail",
  "description": "Turn an image into a compact, shareable thumbnail.",
  "output": "300 px · PNG",
  "acceptedMimeTypes": ["image/jpeg", "image/png", "image/gif", "image/bmp"],
  "inputDescription": "JPEG, PNG, GIF, or BMP",
  "helpText": "Your original stays unchanged. The result is a PNG thumbnail with a 300 px longest edge.",
  "icon": "image",
  "maxFileSizeBytes": 104857600
}
```

Operation metadata and output filename rules live in
[`TaskType.java`](common/src/main/java/io/github/aminbhst/common/core/task/TaskType.java).
Upload limits come from `spring.servlet.multipart.max-file-size`; the API enforces
the same size and accepted MIME rules it advertises. An empty `acceptedMimeTypes`
list means any file type, and a null `maxFileSizeBytes` means no per-file limit.

The frontend loads this catalog once per authenticated workspace and refreshes it
every minute or on manual refresh. It uses the descriptors for shortcuts, operation
choices, filters, descriptions, help, and input validation. Result filenames come
from the download response's `Content-Disposition` header. An unrecognized icon
uses a generic file icon, and tasks missing from the current catalog remain
readable using their identifiers.

To add a new file-based operation, define its metadata in the backend enum and
implement its executor runner. No frontend operation list needs updating. Restart
the API and executor after backend changes. The catalog response replaces the
previous array of identifier strings, so update the API and frontend together.

The existing Hibernate `ddl-auto=update` setting adds the task filename/size and
executor telemetry columns when the updated backend starts. Restart all backend
modules after updating. Existing tasks without filename metadata use their task
ID as a display name.

### Verification

The new API tests use an isolated in-memory database. Run the focused backend
regression suite without requiring the infrastructure services:

```bash
./gradlew :api:test --tests '*DashboardApiTests' :executor:test --tests '*CompressTaskRunnerTests'
```

See [`frontend/README.md`](frontend/README.md) for production builds, browser tests,
API proxy configuration, and troubleshooting.

### Audio/video transcription (Whisper)

`TRANSCRIBE` runs the local OpenAI Whisper CLI through Java `ProcessBuilder`. The executor extracts the first audio track with FFmpeg, transcribes it, and stores a UTF-8 `.txt` result in RustFS. Open a completed task to read its transcript or download the complete TXT file. The preview is limited to 256 KiB; downloads are not truncated.

Configure the allowlist in [`common/src/main/resources/transcription.properties`](common/src/main/resources/transcription.properties), or override the same properties in the API and executor `application.properties` files:

```properties
krite.transcription.models=tiny,base,small
krite.transcription.subtitles-enabled=true
krite.transcription.whisper-executable=whisper
krite.transcription.ffmpeg-executable=ffmpeg
krite.transcription.ffprobe-executable=ffprobe
krite.transcription.device=cpu
krite.transcription.process-timeout=2h
```

The default model list contains only `base`; subtitle rendering is disabled by default. The first configured model is the UI default. An empty model list hides transcription from the catalog and rejects new transcription submissions. Model names are passed to the installed Whisper CLI, so configure names supported by that version (not checkpoint paths). API and **all executors must use the same model allowlist and subtitle setting**. Restart the services after configuration changes; queued jobs are checked against the executor's current policy and fail if their model has since been removed.

Equivalent environment variables are `KRITE_TRANSCRIPTION_MODELS` (comma-separated), `KRITE_TRANSCRIPTION_SUBTITLES_ENABLED`, `KRITE_WHISPER_EXECUTABLE`, `KRITE_FFMPEG_EXECUTABLE`, `KRITE_FFPROBE_EXECUTABLE`, `KRITE_WHISPER_DEVICE`, and `KRITE_TRANSCRIPTION_PROCESS_TIMEOUT`. Executables may be absolute paths but must not contain extra command arguments. The timeout applies separately to each processing command; timed-out commands and their children are terminated.

Each executor needs:

- The **openai-whisper** CLI and its PyTorch dependencies, available as `whisper` to the service account. This is local inference, with no OpenAI API key. Whisper downloads uncached model weights on first use; pre-cache allowed models under that account for offline operation. CPU is the default; large models need substantially more memory and time.
- `ffmpeg` and `ffprobe`, with `libx264`, AAC encoding, and the `subtitles`/libass filter. Fonts must be available to the executor for readable subtitles. Verify `whisper --help`, `ffmpeg -filters`, and `ffprobe -version` in the executor's environment. An absolute FFmpeg executable path also adds its directory to the child process PATH for Whisper.
- Writable temporary storage for the source, extracted WAV, transcript, and optional rendered video. Task temporary directories are removed after success or failure.

When subtitle rendering is enabled, the catalog exposes a checkbox. Selecting it produces a downloadable MP4 with permanent subtitles. Video inputs retain their video and first audio track; audio-only inputs become a 1280×720 black video with their audio and subtitles. Attached album artwork is treated as audio-only. No-speech results can contain an empty transcript and a video without subtitle text. A render failure fails the task; it does not publish a partial successful result.

The frontend gets option definitions, model choices, and defaults from `GET /api/task/types`; it does not maintain a model list. Submit options as a multipart JSON string, for example `options={"model":"base","burnSubtitles":"true"}` alongside `file`. Both the API and executor reject unlisted models and subtitle requests when disabled. Completed task details expose available artifacts. `GET /api/task/{id}/preview` returns `{text,truncated}`, `/result` downloads TXT, and `/video` downloads the subtitled MP4; each endpoint enforces task ownership or admin access.

For an **existing database**, stop the application modules and apply the additive migration before restarting all three updated modules together:

```bash
psql -h localhost -U postgres -d krite -f database/migrations/001-transcription.sql
```

This extends the enum constraint and adds option/result columns while preserving existing tasks. New databases are created by the current Hibernate mappings. The protobuf fields are additive, but older executors cannot execute the new operation, so upgrade the full fleet before allowing transcription submissions.

CLI references: [Whisper](https://github.com/openai/whisper#command-line-usage), [FFmpeg subtitles filter](https://ffmpeg.org/ffmpeg-filters.html#subtitles-1).
