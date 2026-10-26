import type { TaskTypeInfo } from "../src/types";
import { expect, test, type Page } from "@playwright/test";

const now = new Date();
const tasks = [
  {
    id: 128,
    type: "GENERATE_THUMBNAIL",
    filename: "product-launch.png",
    status: "COMPLETE",
    owner: "alice",
    executorId: "executor-01",
    fileSize: 2831224,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    resultAvailable: true,
  },
  {
    id: 127,
    type: "COMPRESS",
    filename: "design-assets.zip",
    status: "IN_PROGRESS",
    owner: "morgan",
    executorId: "executor-02",
    fileSize: 12831224,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    resultAvailable: false,
  },
  {
    id: 126,
    type: "GENERATE_THUMBNAIL",
    filename: "workspace-cover.jpg",
    status: "IN_EXECUTOR_QUEUE",
    owner: "alice",
    executorId: "executor-01",
    fileSize: 1831224,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    resultAvailable: false,
  },
  {
    id: 125,
    type: "COMPRESS",
    filename: "monthly-report.csv",
    status: "COMPLETE",
    owner: "admin",
    executorId: "executor-03",
    fileSize: 831224,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    resultAvailable: true,
  },
  {
    id: 124,
    type: "GENERATE_THUMBNAIL",
    filename: "team-portrait.jpg",
    status: "FAILED",
    owner: "morgan",
    executorId: "executor-02",
    fileSize: 331224,
    createdAt: now.toISOString(),
    updatedAt: now.toISOString(),
    resultAvailable: false,
  },
];
const executors = [1, 2, 3].map((id, index) => ({
  id: `executor-0${id}`,
  hostname: `node-${id}`,
  cpuCores: [8, 4, 8][index],
  cpuLoad: [0.42, 0.67, 0.18][index],
  memoryTotal: 16 * 1024 ** 3,
  memoryFree: [10.2, 7.4, 12.8][index] * 1024 ** 3,
  lastHeartbeat: now.toISOString(),
  online: id !== 3,
  runningTasks: id,
  queuedTasks: 2,
}));

const operations: TaskTypeInfo[] = [
  {
    id: "COMPRESS",
    label: "Compress file",
    description: "Reduce file size with lossless Zstandard compression.",
    output: "Lossless · .zst",
    acceptedMimeTypes: [],
    inputDescription: "Any file type",
    helpText: "Extract the result with zstd.",
    icon: "archive",
    maxFileSizeBytes: 104857600,
  },
  {
    id: "GENERATE_THUMBNAIL",
    label: "Generate thumbnail",
    description: "Turn an image into a compact, shareable thumbnail.",
    output: "300 px · PNG",
    acceptedMimeTypes: ["image/jpeg", "image/png", "image/gif", "image/bmp"],
    inputDescription: "JPEG, PNG, GIF, or BMP",
    helpText:
      "Your original stays unchanged. The result is a PNG thumbnail with a 300 px longest edge.",
    icon: "image",
    maxFileSizeBytes: 104857600,
  },
];

async function mockApi(page: Page, role = "ADMIN", catalog = operations) {
  let submitted: (typeof tasks)[number] | undefined;
  const ownTasks =
    role === "ADMIN" ? tasks : tasks.filter((t) => t.owner === "alice");
  await page.route(/\/(api|auth|admin)\//, async (route) => {
    const url = new URL(route.request().url());
    let data: unknown;
    if (url.pathname === "/auth/login") data = { token: "test-session" };
    else if (url.pathname === "/api/me")
      data = { username: role === "ADMIN" ? "admin" : "alice", role };
    else if (url.pathname === "/api/dashboard")
      data = {
        counts: {
          INITIAL: 2,
          IN_QUEUE: 0,
          IN_COORDINATOR_QUEUE: 3,
          IN_EXECUTOR_QUEUE: 4,
          IN_PROGRESS: 6,
          COMPLETE: 110,
          FAILED: 3,
        },
        submissions: Array.from({ length: 24 }, (_, i) => ({
          time: new Date(now.getTime() - (23 - i) * 3600000).toISOString(),
          count: [
            1, 2, 4, 3, 7, 4, 3, 5, 8, 6, 9, 5, 4, 8, 6, 12, 10, 7, 6, 5, 8, 4,
            3, 2,
          ][i],
        })),
        generatedAt: now.toISOString(),
      };
    else if (url.pathname === "/admin/executors") {
      if (role !== "ADMIN") return route.fulfill({ status: 403 });
      data = executors;
    } else if (url.pathname === "/api/task/types") data = catalog;
    else if (url.pathname === "/api/task") {
      const filtered = ownTasks.filter(
        (task) =>
          (!url.searchParams.get("search") ||
            task.filename.includes(url.searchParams.get("search")!)) &&
          (!url.searchParams.get("status") ||
            task.status === url.searchParams.get("status")),
      );
      data = {
        content: filtered,
        totalElements: filtered.length,
        totalPages: 1,
        number: 0,
      };
    } else if (url.pathname.endsWith("/result")) {
      return route.fulfill({
        body: Buffer.from("test-output"),
        contentType: "application/octet-stream",
        headers: {
          "Content-Disposition":
            "attachment; filename*=UTF-8''product-launch-thumbnail.png",
        },
      });
    } else if (
      route.request().method() === "POST" &&
      url.pathname.startsWith("/api/task/")
    ) {
      submitted = {
        ...tasks[0],
        type: url.pathname.split("/").pop()!,
        id: 129,
        filename:
          route
            .request()
            .postData()
            ?.match(/filename="([^"]+)"/)?.[1] || "upload",
        status: "INITIAL",
        resultAvailable: false,
      };
      return route.fulfill({ status: 201, json: submitted });
    } else if (url.pathname === "/api/task/129") data = submitted;
    else
      data = tasks.find(
        (task) => String(task.id) === url.pathname.split("/").pop(),
      );
    await route.fulfill({ json: data ?? {} });
  });
  await page.goto("/");
  await page
    .getByLabel("Username", { exact: true })
    .fill(role === "ADMIN" ? "admin" : "alice");
  await page.getByLabel("Password", { exact: true }).fill("password123");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Overview." })).toBeVisible();
}

test("admin overview, executor status, filters, and result download", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await mockApi(page);
  await expect(
    page.getByText("product-launch.png", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("42%", { exact: true })).toBeVisible();
  await expect(page.getByText("Offline", { exact: true })).toBeVisible();
  await page.screenshot({
    path: "test-results/admin-overview.png",
    fullPage: true,
  });
  const download = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Download product-launch.png" })
    .click();
  expect((await download).suggestedFilename()).toBe(
    "product-launch-thumbnail.png",
  );
  await page.getByRole("link", { name: "All tasks" }).click();
  await page.getByRole("textbox", { name: "Search tasks" }).fill("monthly");
  await expect(
    page.getByText("monthly-report.csv", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("product-launch.png", { exact: true }),
  ).not.toBeVisible();
  await page.getByRole("textbox", { name: "Search tasks" }).fill("not-found");
  await expect(
    page.getByRole("heading", { name: "No matching tasks" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Clear filters" }).click();
  await expect(
    page.getByText("product-launch.png", { exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
});

test("normal account can upload a supported file and see task details", async ({
  page,
}) => {
  await mockApi(page, "USER");
  await expect(page.getByRole("link", { name: /^Executors/ })).toHaveCount(0);
  await expect(
    page.getByText("design-assets.zip", { exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "New task", exact: true }).click();
  await expect(
    page.getByRole("dialog", { name: "Create a task" }),
  ).toBeVisible();
  await page.getByRole("radio", { name: /Generate thumbnail/ }).check();
  await page.getByLabel("Choose file").setInputFiles({
    name: "invalid.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("hello"),
  });
  await expect(page.getByRole("alert")).toContainText(
    "Choose a file matching: JPEG",
  );
  await page.getByLabel("Choose file").setInputFiles({
    name: "upload.png",
    mimeType: "image/png",
    buffer: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==",
      "base64",
    ),
  });
  await page.screenshot({ path: "test-results/new-task.png", fullPage: true });
  await page.getByRole("button", { name: "Submit task", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Task #0129" })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Download result" }),
  ).toBeDisabled();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("mobile navigation and layout remain usable", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page);
  await expect(
    page.getByText("product-launch.png", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: "test-results/mobile-overview.png",
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: "test-results/mobile-overview.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("link", { name: /^Executors/ }).click();
  await expect(page.getByRole("heading", { name: "Executors." })).toBeVisible();
  await page.getByRole("button", { name: /^Offline/ }).click();
  await expect(
    page.getByRole("heading", { name: "executor-03", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "executor-01", exact: true }),
  ).toHaveCount(0);
});

test("session expiration returns to sign in with an explanation", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/api/task?*", (route) =>
    route.fulfill({ status: 401, json: { message: "Session expired" } }),
  );
  await page.getByRole("link", { name: "All tasks" }).click();
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toContainText(
    "Your session has expired",
  );
});

test("API failures show a retry state without invented data", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/admin/executors", (route) =>
    route.fulfill({ status: 503, json: { message: "Metrics unavailable" } }),
  );
  await page.getByRole("button", { name: "Refresh data" }).click();
  await page.getByRole("link", { name: /^Executors/ }).click();
  await expect(page.getByRole("alert")).toContainText("Metrics unavailable");
  await expect(page.getByRole("button", { name: "Retry" })).toBeVisible();
});

test("download failures stay visible in the open task dialog", async ({
  page,
}) => {
  await mockApi(page, "USER");
  await page.route("**/api/task/128/result", (route) =>
    route.fulfill({
      status: 503,
      json: { message: "File storage is unavailable. Please retry." },
    }),
  );
  await page
    .getByRole("button", { name: /product-launch.png.*Generate thumbnail/ })
    .click();
  const dialog = page.getByRole("dialog", { name: "Task #0128" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "Download result" }).click();
  await expect(dialog.getByRole("alert")).toContainText(
    "File storage is unavailable",
  );
  await expect(
    dialog.getByRole("button", { name: "Download result" }),
  ).toBeEnabled();
});

test("a new backend operation drives shortcuts, validation, submission, details and downloads", async ({
  page,
}) => {
  const operation: TaskTypeInfo = {
    id: "RENDER_DOCUMENT",
    label: "Render a document",
    description: "Render a text file as a PDF.",
    output: "A4 · PDF",
    acceptedMimeTypes: ["text/plain"],
    inputDescription: "Plain text",
    helpText: "Your document will be rendered with the server's page settings.",
    icon: "a-new-icon",
    maxFileSizeBytes: 32,
  };
  await mockApi(page, "USER", [operation]);
  const shortcut = page.getByRole("navigation", { name: "Create a task" });
  await expect(
    shortcut.getByRole("button", { name: "Render a document" }),
  ).toBeVisible();
  await expect(
    shortcut.getByRole("button", { name: "Generate thumbnail" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: "My tasks" }).click();
  await expect(
    page
      .getByLabel("Filter by operation")
      .getByRole("option", { name: operation.label }),
  ).toHaveCount(1);
  // Old task types missing from the catalog remain readable using their identifiers.
  await expect(
    page.getByText("product-launch.png", { exact: true }),
  ).toBeVisible();
  await shortcut.getByRole("button", { name: operation.label }).click();
  const dialog = page.getByRole("dialog", { name: "Create a task" });
  await expect(dialog.getByText(operation.description)).toBeVisible();
  await expect(dialog.getByText(operation.helpText)).toBeVisible();
  await expect(dialog.getByLabel("Choose file")).toHaveAttribute(
    "accept",
    "text/plain",
  );
  await dialog
    .getByLabel("Choose file")
    .setInputFiles({
      name: "big.txt",
      mimeType: "text/plain",
      buffer: Buffer.alloc(33),
    });
  await expect(dialog.getByRole("alert")).toContainText("up to 32 B");
  await dialog
    .getByLabel("Choose file")
    .setInputFiles({
      name: "wrong.png",
      mimeType: "image/png",
      buffer: Buffer.from("wrong"),
    });
  await expect(dialog.getByRole("alert")).toContainText("Plain text");
  await dialog
    .getByLabel("Choose file")
    .setInputFiles({
      name: "notes.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("My document"),
    });
  const submitted = page.waitForRequest(
    (r) =>
      r.method() === "POST" && r.url().endsWith("/api/task/RENDER_DOCUMENT"),
  );
  await dialog.getByRole("button", { name: "Submit task" }).click();
  await submitted;
  const detail = page.getByRole("dialog", { name: "Task #0129" });
  await expect(detail.getByText("A4 · PDF")).toBeVisible();
  await page.route("**/api/task/129", (route) =>
    route.fulfill({
      json: {
        ...tasks[0],
        id: 129,
        type: operation.id,
        filename: "notes.txt",
        resultAvailable: true,
        status: "COMPLETE",
      },
    }),
  );
  await page.route("**/api/task/129/result", (route) =>
    route.fulfill({
      body: "%PDF-test",
      contentType: "application/pdf",
      headers: {
        "Content-Disposition":
          "attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
      },
    }),
  );
  await expect(
    detail.getByRole("button", { name: "Download result" }),
  ).toBeEnabled({ timeout: 10_000 });
  const download = page.waitForEvent("download");
  await detail.getByRole("button", { name: "Download result" }).click();
  expect((await download).suggestedFilename()).toBe("résumé.pdf");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Help & documentation" }).click();
  await expect(
    page.getByRole("dialog").getByText(operation.helpText, { exact: false }),
  ).toBeVisible();
});

test("catalog failures are retryable and an empty catalog cannot submit", async ({
  page,
}) => {
  await mockApi(page, "USER", []);
  await page.route("**/api/task/types", (route) =>
    route.fulfill({ status: 503, json: { message: "Catalog unavailable" } }),
  );
  await page.getByRole("button", { name: "Refresh data" }).click();
  await page.getByRole("button", { name: "New task", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Create a task" });
  await expect(dialog.getByRole("alert")).toContainText("Catalog unavailable");
  await expect(
    dialog.getByRole("button", { name: "Submit task" }),
  ).toBeDisabled();
  await page.route("**/api/task/types", (route) => route.fulfill({ json: [] }));
  await dialog.getByRole("button", { name: "Retry" }).click();
  await expect(
    dialog.getByText("No operations are currently available."),
  ).toBeVisible();
  await expect(dialog.getByLabel("Choose file")).toBeDisabled();
  await expect(dialog.getByRole("radio")).toHaveCount(0);
});

const transcription: TaskTypeInfo = {
  id: "TRANSCRIBE", label: "Transcribe audio or video", description: "Transcribe speech with Whisper.",
  output: "TXT · optional subtitled MP4", acceptedMimeTypes: ["audio/*", "video/*"],
  inputDescription: "Audio or video", helpText: "Read the transcript or download your files.", icon: "file", maxFileSizeBytes: 104857600,
  options: [
    { name: "model", label: "Whisper model", kind: "select", choices: ["tiny", "base"], defaultValue: "tiny" },
    { name: "burnSubtitles", label: "Create a video with permanent subtitles", kind: "boolean", choices: [], defaultValue: "false" },
  ],
};

test("transcription options come from the catalog and results offer text and video", async ({ page }) => {
  await mockApi(page, "USER", [transcription]);
  await page.getByRole("button", { name: "New task", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Create a task" });
  await expect(dialog.getByLabel("Whisper model").getByRole("option")).toHaveText(["tiny", "base"]);
  await dialog.getByLabel("Whisper model").selectOption("base");
  await dialog.getByLabel("Create a video with permanent subtitles").check();
  await dialog.getByLabel("Choose file").setInputFiles({ name: "interview.wav", mimeType: "audio/wav", buffer: Buffer.from("fixture") });
  await page.screenshot({ path: "test-results/transcription-options.png", fullPage: true });
  const submitted = page.waitForRequest(r => r.method() === "POST" && r.url().endsWith("/api/task/TRANSCRIBE"));
  await dialog.getByRole("button", { name: "Submit task" }).click();
  expect((await submitted).postData()).toContain('{"model":"base","burnSubtitles":"true"}');
  const detail = page.getByRole("dialog", { name: "Task #0129" });
  await expect(detail).toBeVisible();
  await page.route("**/api/task/129", route => route.fulfill({ json: {
    ...tasks[0], id: 129, type: "TRANSCRIBE", filename: "interview.wav", textPreviewAvailable: true,
    options: { model: "base", burnSubtitles: "true" },
    artifacts: [{path: "result", label: "Download transcript (.txt)"}, {path: "video", label: "Download subtitled video (.mp4)"}],
  }}));
  await page.route("**/api/task/129/preview", route => route.fulfill({json: { text: "Hello from the interview. <script>plain text</script>", truncated: false }}));
  for (const [path, filename] of [["result", "interview-transcript.txt"], ["video", "interview-subtitled.mp4"]]) {
    await page.route(`**/api/task/129/${path}`, route => route.fulfill({ body: "fixture", headers: { "Content-Disposition": `attachment; filename="${filename}"` } }));
  }
  await expect(detail.getByText("Hello from the interview.", {exact: false})).toBeVisible({timeout: 10000});
  await page.screenshot({path: "test-results/transcript-result.png", fullPage: true});
  for (const [label, filename] of [["Download transcript (.txt)", "interview-transcript.txt"], ["Download subtitled video (.mp4)", "interview-subtitled.mp4"]]) {
    const download = page.waitForEvent("download");
    await detail.getByRole("button", {name: label, exact: true}).click();
    expect((await download).suggestedFilename()).toBe(filename);
  }
});

test("disabled subtitle rendering is absent and removed models are reconciled", async ({ page }) => {
  await mockApi(page, "USER", [{...transcription, options: transcription.options!.slice(0, 1)}]);
  await page.getByRole("button", { name: "New task", exact: true }).click();
  await expect(page.getByLabel("Create a video with permanent subtitles")).toHaveCount(0);
  await page.getByLabel("Whisper model").selectOption("base");
  await page.keyboard.press("Escape");
  await page.route("**/api/task/types", route => route.fulfill({json: [{...transcription, options: [{...transcription.options![0], choices: ["tiny"], defaultValue: "tiny"}]}]}));
  await page.getByRole("button", {name: "Refresh data"}).click();
  await page.getByRole("button", { name: "New task", exact: true }).click();
  await expect(page.getByLabel("Whisper model").getByRole("option")).toHaveText(["tiny"]);
  await expect(page.getByLabel("Whisper model")).toHaveValue("tiny");
});
