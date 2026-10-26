package io.github.aminbhst.common.core.task;

import lombok.Getter;
import java.util.List;
import org.springframework.util.MimeTypeUtils;

/** Each operation owns its public catalog metadata and file contract. */
@Getter
public enum TaskType {
    COMPRESS(
            "Compress file", "Reduce file size with lossless Zstandard compression.",
            "Lossless · .zst", List.of(), "Any file type", "archive",
            "Your original stays unchanged. The result is a lossless Zstandard archive (.zst). " +
                    "Extract it with a compatible tool or zstd -d filename.zst. This is file compression, not image format conversion.",
            ".zst", false),
    GENERATE_THUMBNAIL(
            "Generate thumbnail", "Turn an image into a compact, shareable thumbnail.",
            "300 px · PNG", List.of("image/jpeg", "image/png", "image/gif", "image/bmp"),
            "JPEG, PNG, GIF, or BMP", "image",
            "Your original stays unchanged. The result is a PNG thumbnail with a 300 px longest edge.",
            "-thumbnail.png", true),
    TRANSCRIBE("Transcribe audio or video", "Turn speech into a readable transcript with Whisper.",
            "TXT · optional subtitled MP4", List.of("audio/*", "video/*", "application/ogg"),
            "Audio or video with a speech track", "file",
            "Download the transcript as text. When enabled, you can also create a video with permanent subtitles; audio inputs use a black background.",
            "-transcript.txt", true);

    private final String label;
    private final String description;
    private final String output;
    private final List<String> acceptedMimeTypes;
    private final String inputDescription;
    private final String icon;
    private final String helpText;
    private final String outputSuffix;
    private final boolean replaceExtension;

    TaskType(String label, String description, String output, List<String> acceptedMimeTypes,
             String inputDescription, String icon, String helpText, String outputSuffix, boolean replaceExtension) {
        this.label = label;
        this.description = description;
        this.output = output;
        this.acceptedMimeTypes = List.copyOf(acceptedMimeTypes);
        this.inputDescription = inputDescription;
        this.icon = icon;
        this.helpText = helpText;
        this.outputSuffix = outputSuffix;
        this.replaceExtension = replaceExtension;
    }

    public boolean accepts(String contentType) {
        if (acceptedMimeTypes.isEmpty()) return true;
        if (contentType == null) return false;
        try {
            var actual = MimeTypeUtils.parseMimeType(contentType);
            return acceptedMimeTypes.stream().anyMatch(type -> MimeTypeUtils.parseMimeType(type).includes(actual));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String resultFilename(String originalFilename) {
        return (replaceExtension ? originalFilename.replaceFirst("\\.[^.]+$", "") : originalFilename) + outputSuffix;
    }
}
