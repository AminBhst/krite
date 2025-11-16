package io.github.aminbhst.api.controller;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.common.persistence.repository.UserRepository;
import io.github.aminbhst.common.storage.StorageService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final TaskRepository taskRepository;

    private final UserRepository userRepository;

    private final StorageService storageService;

    public TaskController(TaskRepository taskRepository,
                          UserRepository userRepository,
                          StorageService storageService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/{taskType}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> createTask(
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            @PathVariable String taskType) throws ServletException, IOException {

        Task task = new Task();
        try {
            task.setType(TaskType.valueOf(taskType));
        } catch (Throwable t) {
            return ResponseEntity
                    .badRequest()
                    .body("Invalid task type! possible values are: " + Arrays.toString(TaskType.values()));
        }

        var file = request.getPart("file");
        String objectKey = storageService.upload(
                file.getInputStream(),
                file.getSize(),
                file.getContentType()
        );

        task.setCreatedAt(LocalDateTime.now());
        task.setStatus(TaskStatus.INITIAL);
        task.setType(TaskType.valueOf(taskType.toUpperCase()));
        task.setCreatedBy(userRepository.findByUsername(user.getUsername()).orElseThrow());
        task.setSourceFileObjectKey(objectKey);

        taskRepository.save(task);

        return ResponseEntity.ok().build();
    }

}
