package io.github.aminbhst.executor.runner;

import io.github.aminbhst.common.core.task.TranscriptionProperties;
import io.github.aminbhst.common.storage.StorageService;
import io.github.aminbhst.coordinator.CoordinatorProto;
import io.github.aminbhst.executor.persistence.repository.ExecutorLogRepository;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscribeTaskRunnerTests {
    @TempDir Path directory;
    private final StorageService storage = mock(StorageService.class);
    private final ExecutorLogRepository logs = mock(ExecutorLogRepository.class);
    private final ExecutorTaskQueue queue = new ExecutorTaskQueue();
    private final TranscriptionProperties config = new TranscriptionProperties();

    private Path script(String name, String body) throws Exception {
        Path path = directory.resolve(name);
        Files.writeString(path, "#!/bin/sh\nset -eu\n" + body);
        assertTrue(path.toFile().setExecutable(true));
        return path;
    }

    private TranscribeTaskRunner runner(boolean subtitles) {
        var runner = new TranscribeTaskRunner(logs, queue, storage, config);
        runner.withTask(CoordinatorProto.TaskAssignment.newBuilder().setTaskId(1).setTaskType("TRANSCRIBE")
                .setSourceFileObjectKey("source").setModel("base").setBurnSubtitles(subtitles).build());
        return runner;
    }

    private void fakeWhisper() throws Exception {
        config.setWhisperExecutable(script("whisper-test", """
                test "$1" = speech.wav
                test "$2" = --model
                test "$3" = base
                printf 'Hello from the transcript.\\n' > speech.txt
                printf '1\\n00:00:00,000 --> 00:00:01,000\\nHello from the transcript.\\n\\n' > speech.srt
                """).toString());
    }

    @Test
    void rejectsUnlistedModelsAndDisabledSubtitlesBeforeDownloading() {
        config.setModels(List.of("tiny"));
        assertThrows(IllegalArgumentException.class, () -> runner(false).runInternal());
        config.setModels(List.of("base"));
        assertThrows(IllegalArgumentException.class, () -> runner(true).runInternal());
        verifyNoInteractions(storage);
    }

    @Test
    void processFailureReportsFailedStatusAndDoesNotUpload() throws Exception {
        config.setFfmpegExecutable(script("failure", "echo 'invalid media' >&2\nexit 7\n").toString());
        when(storage.download("source")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        runner(false).run();
        assertEquals("FAILED", queue.statusSnapshot().getFirst().getStatus());
        verify(storage, never()).upload(any(), anyLong(), any());
        verify(logs).save(any());
    }

    @Test
    void subprocessTimeoutIsBounded() throws Exception {
        config.setProcessTimeout(Duration.ofMillis(100));
        Path slow = script("slow", "sleep 30\n");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Exception error = assertThrows(Exception.class, () -> runner(false).execute(directory, List.of(slow.toString())));
            assertTrue(error.getMessage().contains("timed out"));
        });
    }

    @Test
    void largeProcessOutputIsDrainedAndBounded() throws Exception {
        Path noisy = script("noisy", "head -c 100000 /dev/zero\n");
        assertEquals(16384, runner(false).execute(directory, List.of(noisy.toString())).length());
    }

    @Test
    void transcriptOnlyUploadsTextAndCleansWorkingDirectory() throws Exception {
        fakeWhisper();
        Path marker = directory.resolve("workdir");
        config.setFfmpegExecutable(script("ffmpeg-test", "pwd > '" + marker + "'\ntouch speech.wav\n").toString());
        when(storage.download("source")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(storage.upload(any(), anyLong(), eq("text/plain; charset=utf-8"))).thenAnswer(call -> {
            assertEquals("Hello from the transcript.\n", new String(((InputStream) call.getArgument(0)).readAllBytes()));
            return "text-key";
        });
        runner(false).run();
        var completed = queue.statusSnapshot().getFirst();
        assertEquals("COMPLETE", completed.getStatus());
        assertEquals("text-key", completed.getOutputObjectKey());
        assertEquals("", completed.getVideoObjectKey());
        assertFalse(Files.exists(Path.of(Files.readString(marker).trim())));
    }

    @Test
    void realFfmpegRendersBothAudioAndVideoWithSubtitles() throws Exception {
        // Optional on machines without media binaries; CI can provide FFmpeg on PATH.
        org.junit.jupiter.api.Assumptions.assumeTrue(new ProcessBuilder("sh", "-c", "command -v ffmpeg && command -v ffprobe")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0);
        fakeWhisper();
        config.setSubtitlesEnabled(true);
        config.setProcessTimeout(Duration.ofSeconds(30));
        for (boolean video : List.of(false, true)) {
            Path source = directory.resolve(video ? "source.mp4" : "source.wav");
            var fixture = new java.util.ArrayList<>(List.of("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=1.2"));
            if (video) fixture.addAll(List.of("-f", "lavfi", "-i", "color=c=blue:s=320x240:r=25:d=1.2", "-c:v", "libx264", "-c:a", "aac"));
            fixture.add(source.toString());
            runner(false).execute(directory, fixture);
            doAnswer(call -> Files.newInputStream(source)).when(storage).download("source");
            Map<String, byte[]> uploads = new HashMap<>();
            doAnswer(call -> {
                String mime = call.getArgument(2);
                uploads.put(mime, ((InputStream) call.getArgument(0)).readAllBytes());
                return mime.equals("video/mp4") ? "video-key" : "text-key";
            }).when(storage).upload(any(), anyLong(), any());
            var runner = runner(true);
            runner.run();
            var completed = queue.statusSnapshot().getFirst();
            assertEquals("COMPLETE", completed.getStatus());
            assertEquals("video-key", completed.getVideoObjectKey());
            assertEquals("text-key", completed.getOutputObjectKey());
            Path rendered = directory.resolve("rendered.mp4");
            Files.write(rendered, uploads.get("video/mp4"));
            String streams = runner.execute(directory, List.of("ffprobe", "-v", "error", "-show_entries", "stream=codec_type,width,height", "-of", "csv=p=0", rendered.toString()));
            assertTrue(streams.contains("audio"));
            assertTrue(streams.contains(video ? "320,240" : "1280,720"));
            // Decode a frame and verify light subtitle pixels exist on the black/blue background.
            Path frame = directory.resolve("frame.png");
            runner.execute(directory, List.of("ffmpeg", "-y", "-v", "error", "-ss", "0.5", "-i", rendered.toString(), "-frames:v", "1", frame.toString()));
            var image = javax.imageio.ImageIO.read(frame.toFile());
            int lightPixels = 0;
            for (int y = image.getHeight() / 2; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getRGB(x, y);
                    if (((color >> 16) & 255) > 150 && ((color >> 8) & 255) > 150 && (color & 255) > 150) lightPixels++;
                }
            assertTrue(lightPixels > 20, "Subtitles should be burned into video pixels");
        }
    }
}
