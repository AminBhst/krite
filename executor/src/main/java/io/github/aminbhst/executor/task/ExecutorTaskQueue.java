package io.github.aminbhst.executor.task;

import io.github.aminbhst.coordinator.CoordinatorProto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ExecutorTaskQueue {

    private final Queue<CoordinatorProto.TaskAssignment> taskQueueAssignment = new ConcurrentLinkedQueue<>();

    public final List<CoordinatorProto.TaskStatus> taskStatuses = new CopyOnWriteArrayList<>();

    public void addTaskStatus(CoordinatorProto.TaskStatus taskStatus) {
        taskStatuses.removeIf(status -> status.getTaskId() == taskStatus.getTaskId());
        taskStatuses.add(taskStatus);
    }

    public void pushTask(CoordinatorProto.TaskAssignment task) {
        taskQueueAssignment.add(task);
    }

    public CoordinatorProto.TaskAssignment pollTask() {
        return taskQueueAssignment.poll();
    }

    public int size() {
        return taskQueueAssignment.size();
    }
}
