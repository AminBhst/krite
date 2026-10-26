package io.github.aminbhst.api.controller;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.common.persistence.repository.UserRepository;
import io.github.aminbhst.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MultipartProperties multipartProperties;
    private final io.github.aminbhst.common.core.task.TranscriptionProperties transcription;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public record OptionInfo(String name, String label, String kind, List<String> choices, String defaultValue) {}

    private List<OptionInfo> options(TaskType type) {
        if (type != TaskType.TRANSCRIBE) return List.of();
        var fields = new java.util.ArrayList<OptionInfo>();
        fields.add(new OptionInfo("model", "Whisper model", "select", transcription.getModels(),
                transcription.getModels().getFirst()));
        if (transcription.isSubtitlesEnabled())
            fields.add(new OptionInfo("burnSubtitles", "Create a video with permanent subtitles", "boolean", List.of(), "false"));
        return fields;
    }

    public static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/types")
    public List<TaskTypeInfo> types() {
        long maximum = multipartProperties.getMaxFileSize().toBytes();
        return java.util.Arrays.stream(TaskType.values())
                .filter(type -> type != TaskType.TRANSCRIBE || !transcription.getModels().isEmpty()).map(type -> new TaskTypeInfo(
                type.name(), type.getLabel(), type.getDescription(), type.getOutput(),
                type.getAcceptedMimeTypes(), type.getInputDescription(), type.getHelpText(), type.getIcon(),
                maximum < 0 ? null : maximum, options(type))).toList();
    }

    public record TaskTypeInfo(String id, String label, String description, String output,
                               List<String> acceptedMimeTypes, String inputDescription, String helpText,
                               String icon, Long maxFileSizeBytes, List<OptionInfo> options) {}

    public record TaskPage(List<TaskView> content, long totalElements, int totalPages, int number) {}

    @GetMapping
    @Transactional(readOnly = true)
    public TaskPage list(Authentication auth, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "15") int size,
                         @RequestParam(required = false) TaskStatus status,
                         @RequestParam(required = false) TaskType type,
                         @RequestParam(defaultValue = "") String search) {
        Specification<Task> spec = (root, query, cb) -> isAdmin(auth) ? cb.conjunction()
                : cb.equal(root.get("createdBy").get("username"), auth.getName());
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (type != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        if (!search.isBlank()) {
            String term = "%" + search.trim().toLowerCase().replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("originalFilename")), term, '\\'));
        }
        var result = taskRepository.findAll(spec, PageRequest.of(Math.max(0, page),
                Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new TaskPage(result.getContent().stream().map(TaskView::of).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    @PostMapping(value = "/{taskType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<TaskView> createTask(Authentication auth, @PathVariable TaskType taskType,
                                               @RequestParam("file") MultipartFile file,
                                               @RequestParam(defaultValue = "{}") String options) throws IOException {
        java.util.Map<String, String> values;
        try {
            var node = objectMapper.readTree(options);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Options must be an object.");
            values = new java.util.HashMap<>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!field.getValue().isTextual()) throw new IllegalArgumentException("Option values must be strings.");
                values.put(field.getKey(), field.getValue().textValue());
            }
            if (taskType == TaskType.TRANSCRIBE) transcription.validate(values);
            else if (!values.isEmpty()) throw new IllegalArgumentException("This operation has no options.");
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid task options: " + e.getMessage());
        }
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty file.");
        long maximum = multipartProperties.getMaxFileSize().toBytes();
        if (maximum >= 0 && file.getSize() > maximum) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file exceeds the configured upload size limit.");
        }
        if (!taskType.accepts(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file matching: " + taskType.getInputDescription() + ".");
        }
        Task task = new Task();
        task.setType(taskType);
        task.setTranscriptionModel(values.get("model"));
        task.setBurnSubtitles(Boolean.parseBoolean(values.get("burnSubtitles")));
        task.setCreatedBy(userRepository.findByUsername(auth.getName()).orElseThrow());
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        task.setOriginalFilename(name.isBlank() ? "upload" : name);
        task.setFileSize(file.getSize());
        task.setSourceFileObjectKey(storageService.upload(file.getInputStream(), file.getSize(), file.getContentType()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskView.of(taskRepository.save(task)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public TaskView detail(Authentication auth, @PathVariable long id) {
        return TaskView.of(authorizedTask(auth, id));
    }

    @GetMapping("/{id}/result")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> result(Authentication auth, @PathVariable long id) throws Exception {
        Task task = authorizedTask(auth, id);
        if (!TaskView.of(task).resultAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This task has no result to download yet.");
        }
        String name = task.getOriginalFilename() == null ? "task-" + id : task.getOriginalFilename();
        String filename = task.getType().resultFilename(name);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(storageService.download(task.getOutputFileObjectKey())));
    }

    @GetMapping("/{id}/preview")
    @Transactional(readOnly = true)
    public TranscriptPreview preview(Authentication auth, @PathVariable long id) throws Exception {
        Task task = authorizedTask(auth, id);
        if (!TaskView.of(task).textPreviewAvailable())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No text preview is available yet.");
        try (var input = storageService.download(task.getOutputFileObjectKey())) {
            // Bound preview memory; the download always contains the entire transcript.
            byte[] bytes = input.readNBytes(262145);
            boolean truncated = bytes.length > 262144;
            return new TranscriptPreview(new String(bytes, 0, Math.min(bytes.length, 262144), StandardCharsets.UTF_8), truncated);
        }
    }

    public record TranscriptPreview(String text, boolean truncated) {}

    @GetMapping("/{id}/video")
    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> video(Authentication auth, @PathVariable long id) throws Exception {
        Task task = authorizedTask(auth, id);
        if (task.getStatus() != TaskStatus.COMPLETE || task.getVideoFileObjectKey() == null || task.getVideoFileObjectKey().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No subtitled video is available yet.");
        String name = task.getOriginalFilename() == null ? "task-" + id : task.getOriginalFilename();
        return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name.replaceFirst("\\.[^.]+$", "") + "-subtitled.mp4", StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(storageService.download(task.getVideoFileObjectKey())));
    }

    private Task authorizedTask(Authentication auth, long id) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isAdmin(auth) && (task.getCreatedBy() == null || !task.getCreatedBy().getUsername().equals(auth.getName()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return task;
    }
}
