package io.github.aminbhst.common.core.task;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Data
@Component
@org.springframework.context.annotation.PropertySource("classpath:transcription.properties")
@ConfigurationProperties(prefix = "krite.transcription")
public class TranscriptionProperties {
    private List<String> models = List.of("base");
    private boolean subtitlesEnabled = false;
    private String whisperExecutable = "whisper";
    private String ffmpegExecutable = "ffmpeg";
    private String ffprobeExecutable = "ffprobe";
    private String device = "cpu";
    private Duration processTimeout = Duration.ofHours(2);

    @PostConstruct
    public void checkConfiguration() {
        if (models == null || models.stream().anyMatch(m -> m == null || !m.matches("[A-Za-z0-9][A-Za-z0-9._-]*")))
            throw new IllegalArgumentException("Transcription models must be model names, not paths or CLI arguments.");
        models = models.stream().distinct().toList();
        if (processTimeout == null || processTimeout.isNegative() || processTimeout.isZero())
            throw new IllegalArgumentException("Transcription process timeout must be positive.");
    }

    public void validate(Map<String, String> options) {
        if (!options.keySet().stream().allMatch(k -> k.equals("model") || k.equals("burnSubtitles")))
            throw new IllegalArgumentException("Unknown transcription option.");
        if (options.get("model") == null || !models.contains(options.get("model")))
            throw new IllegalArgumentException("Choose a model from the configured model list.");
        String burn = options.getOrDefault("burnSubtitles", "false");
        if (!burn.equals("true") && !burn.equals("false"))
            throw new IllegalArgumentException("burnSubtitles must be true or false.");
        if (burn.equals("true") && !subtitlesEnabled)
            throw new IllegalArgumentException("Subtitle rendering is disabled.");
    }
}
