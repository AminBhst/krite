package io.github.aminbhst.api.controller;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.persistence.entity.Task;
import java.time.LocalDateTime;

public record TaskView(Long id, TaskType type, TaskStatus status, String filename, Long fileSize,
                       String owner, String executorId, LocalDateTime createdAt,
                       LocalDateTime updatedAt, boolean resultAvailable, boolean textPreviewAvailable,
                       java.util.List<Artifact> artifacts, java.util.Map<String, String> options) {
    public record Artifact(String path, String label) {}
    public static TaskView of(Task task) {
        boolean ready = task.getStatus() == TaskStatus.COMPLETE && task.getOutputFileObjectKey() != null
                && !task.getOutputFileObjectKey().isBlank();
        var artifacts = new java.util.ArrayList<Artifact>();
        if (ready) artifacts.add(new Artifact("result", task.getType() == TaskType.TRANSCRIBE ? "Download transcript (.txt)" : "Download result"));
        if (ready && task.getVideoFileObjectKey() != null && !task.getVideoFileObjectKey().isBlank())
            artifacts.add(new Artifact("video", "Download subtitled video (.mp4)"));
        var options = new java.util.LinkedHashMap<String, String>();
        if (task.getTranscriptionModel() != null) {
            options.put("model", task.getTranscriptionModel());
            options.put("burnSubtitles", Boolean.toString(task.isBurnSubtitles()));
        }
        return new TaskView(task.getId(), task.getType(), task.getStatus(),
                task.getOriginalFilename() == null ? "Task " + task.getId() : task.getOriginalFilename(),
                task.getFileSize(), task.getCreatedBy() == null ? null : task.getCreatedBy().getUsername(),
                task.getAssignedNode(), task.getCreatedAt(), task.getUpdatedAt(),
                ready, ready && task.getType() == TaskType.TRANSCRIBE, artifacts, options);
    }
}
