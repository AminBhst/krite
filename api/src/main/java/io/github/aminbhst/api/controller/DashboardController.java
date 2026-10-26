package io.github.aminbhst.api.controller;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.persistence.repository.ExecutorNodeRepository;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.common.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class DashboardController {
    private final TaskRepository tasks;
    private final ExecutorNodeRepository executors;
    private final UserRepository users;

    public record Account(String username, String role) {}
    public record Bucket(LocalDateTime time, long count) {}
    public record Summary(Map<TaskStatus, Long> counts, List<Bucket> submissions, LocalDateTime generatedAt) {}
    public record ExecutorView(String id, String hostname, int cpuCores, double cpuLoad,
                               long memoryTotal, long memoryFree, LocalDateTime lastHeartbeat,
                               boolean online, long runningTasks, long queuedTasks) {}

    @GetMapping("/api/me")
    public Account me(Authentication auth) {
        var user = users.findByUsername(auth.getName()).orElseThrow();
        return new Account(user.getUsername(), user.getRole());
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public Summary summary(Authentication auth) {
        String owner = TaskController.isAdmin(auth) ? null : auth.getName();
        Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) counts.put(status, 0L);
        tasks.countByStatus(owner).forEach(c -> counts.put(c.getStatus(), c.getTotal()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.truncatedTo(ChronoUnit.HOURS).minusHours(23);
        var times = tasks.submissionTimes(owner, start);
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            LocalDateTime time = start.plusHours(i);
            long count = times.stream().filter(t -> !t.isBefore(time) && t.isBefore(time.plusHours(1))).count();
            buckets.add(new Bucket(time, count));
        }
        return new Summary(counts, buckets, now);
    }

    @GetMapping("/admin/executors")
    @Transactional(readOnly = true)
    public List<ExecutorView> executors() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(10);
        return executors.findAll().stream()
                .filter(e -> e.getExecutorId() != null)
                .sorted(Comparator.comparing(e -> e.getExecutorId()))
                .map(e -> new ExecutorView(e.getExecutorId(), e.getHostname(), e.getCpuCores(), e.getCpuLoad(),
                        e.getMemoryTotal(), e.getMemoryFree(), e.getLastHeartbeat(),
                        e.getLastHeartbeat() != null && e.getLastHeartbeat().isAfter(cutoff),
                        tasks.countByAssignedNodeAndStatusIn(e.getExecutorId(), List.of(TaskStatus.IN_PROGRESS)),
                        tasks.countByAssignedNodeAndStatusIn(e.getExecutorId(),
                                List.of(TaskStatus.IN_EXECUTOR_QUEUE, TaskStatus.IN_COORDINATOR_QUEUE))))
                .toList();
    }
}
