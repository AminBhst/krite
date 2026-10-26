package io.github.aminbhst.executor.runner;

import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.core.task.TranscriptionProperties;
import io.github.aminbhst.common.storage.StorageService;
import io.github.aminbhst.executor.persistence.repository.ExecutorLogRepository;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TranscribeTaskRunner extends TaskRunner {
    private final StorageService storage;
    private final TranscriptionProperties config;

    public TranscribeTaskRunner(ExecutorLogRepository logs, ExecutorTaskQueue queue,
                                StorageService storage, TranscriptionProperties config) {
        super(logs, queue);
        this.storage = storage;
        this.config = config;
    }

    @Override
    public String getTaskType() { return TaskType.TRANSCRIBE.name(); }

    @Override
    protected String runInternal() throws Exception {
        config.validate(Map.of("model", task.getModel(), "burnSubtitles", Boolean.toString(task.getBurnSubtitles())));
        Path directory = Files.createTempDirectory("krite-transcribe-");
        try {
            try (var input = storage.download(task.getSourceFileObjectKey())) {
                Files.copy(input, directory.resolve("source.media"));
            }
            // Normalize the first audio track before Whisper. No original filenames enter commands.
            execute(directory, List.of(config.getFfmpegExecutable(), "-nostdin", "-y", "-v", "error",
                    "-i", "source.media", "-map", "0:a:0", "-vn", "-ac", "1", "-ar", "16000", "speech.wav"));
            execute(directory, List.of(config.getWhisperExecutable(), "speech.wav", "--model", task.getModel(),
                    "--device", config.getDevice(), "--fp16", "False", "--task", "transcribe",
                    "--output_dir", ".", "--output_format", "all", "--verbose", "False",
                    "--word_timestamps", "True", "--max_line_width", "42", "--max_line_count", "2"));
            Path transcript = directory.resolve("speech.txt");
            if (!Files.isRegularFile(transcript)) throw new IOException("Whisper did not produce a transcript.");
            if (task.getBurnSubtitles()) {
                Path subtitles = directory.resolve("speech.srt");
                if (!Files.isRegularFile(subtitles)) throw new IOException("Whisper did not produce subtitles.");
                // Uppercase V excludes attached cover artwork in audio containers.
                String videoStream = execute(directory, List.of(config.getFfprobeExecutable(), "-v", "error",
                        "-select_streams", "V:0", "-show_entries", "stream=index", "-of", "csv=p=0", "source.media"));
                var command = new ArrayList<>(List.of(config.getFfmpegExecutable(), "-nostdin", "-y", "-v", "error"));
                if (videoStream.isBlank()) {
                    command.addAll(List.of("-f", "lavfi", "-i", "color=c=black:s=1280x720:r=25",
                            "-i", "source.media", "-map", "0:v:0", "-map", "1:a:0", "-shortest"));
                } else {
                    command.addAll(List.of("-i", "source.media", "-map", "0:V:0", "-map", "0:a:0"));
                }
                // Empty speech is valid: render the video without a subtitles filter in that case.
                String filter = "scale=trunc(iw/2)*2:trunc(ih/2)*2";
                if (Files.size(subtitles) > 0) filter += ",subtitles=filename=speech.srt";
                command.addAll(List.of("-vf", filter, "-c:v", "libx264", "-preset", "medium", "-crf", "23",
                        "-pix_fmt", "yuv420p", "-c:a", "aac", "-movflags", "+faststart", "subtitled.mp4"));
                execute(directory, command);
                videoObjectKey = upload(directory.resolve("subtitled.mp4"), "video/mp4");
            }
            return upload(transcript, "text/plain; charset=utf-8");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private String upload(Path file, String contentType) throws IOException {
        try (var input = Files.newInputStream(file)) {
            return storage.upload(input, Files.size(file), contentType);
        }
    }

    /** Drain output continuously, retain only a bounded diagnostic, and terminate timed-out children. */
    String execute(Path directory, List<String> command) throws Exception {
        var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        // Whisper itself also calls ffmpeg by name when reading audio.
        Path ffmpeg = Path.of(config.getFfmpegExecutable());
        if (ffmpeg.isAbsolute()) builder.environment().put("PATH", ffmpeg.getParent() + ":" + builder.environment().getOrDefault("PATH", ""));
        Process process = builder.start();
        process.getOutputStream().close();
        var output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var stream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    int remaining = 16384 - output.size();
                    if (remaining > 0) output.write(buffer, 0, Math.min(count, remaining));
                }
            } catch (IOException ignored) { /* Closing a terminated process ends the reader. */ }
        });
        try {
            if (!process.waitFor(config.getProcessTimeout().toMillis(), TimeUnit.MILLISECONDS))
                throw new IOException("Transcription command timed out: " + command.getFirst());
            reader.join(5000);
            if (reader.isAlive()) throw new IOException("Command output did not close: " + command.getFirst());
            String diagnostic = output.toString(StandardCharsets.UTF_8);
            if (process.exitValue() != 0)
                throw new IOException("Command failed (" + process.exitValue() + "): " + command.getFirst() + "\n" + diagnostic);
            return diagnostic;
        } finally {
            process.descendants().forEach(child -> child.destroyForcibly());
            if (process.isAlive()) process.destroyForcibly();
            process.getInputStream().close();
            reader.join(5000);
        }
    }
}
