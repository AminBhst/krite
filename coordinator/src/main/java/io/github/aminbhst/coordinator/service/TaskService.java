package io.github.aminbhst.coordinator.service;

import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.coordinator.executor.ExecutorRegistry;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ExecutorRegistry executorRegistry;

    @Transactional
    public List<Task> findPendingTasks(Pageable pageable) {
        return taskRepository.findNextPendingTasks(pageable)
                .stream()
                .filter(executorRegistry::taskNotInQueue)
                .toList();
    }
}
