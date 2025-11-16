package io.github.aminbhst.executor.runner;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.coordinator.CoordinatorProto;
import io.github.aminbhst.executor.persistence.entity.ExecutorLog;
import io.github.aminbhst.executor.persistence.repository.ExecutorLogRepository;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public abstract class TaskRunner implements Runnable {

    protected CoordinatorProto.TaskAssignment task;

    private final ExecutorLogRepository executorLogRepository;

    private final ExecutorTaskQueue executorTaskQueue;

    protected TaskRunner(ExecutorLogRepository executorLogRepository, ExecutorTaskQueue executorTaskQueue) {
        this.executorLogRepository = executorLogRepository;
        this.executorTaskQueue = executorTaskQueue;
    }

    abstract String getTaskType();

    abstract protected String runInternal() throws Exception;

    @Override
    public void run() {
        try {
            log.info("Running task {} for taskId {}", getTaskType(), task.getTaskId());
            addStatusToQueue(TaskStatus.IN_PROGRESS);
            String outputObjectKey = runInternal();
            log.info("Task {} with id {} finished with outputObjectKey {}", getTaskType(), task.getTaskId(), outputObjectKey);
            addCompleteStatus(outputObjectKey);
        } catch (Exception e) {
            log.error("Failed to run task {} for taskId {}", getTaskType(), task.getTaskId(), e);
            ExecutorLog log = new ExecutorLog();
            log.setTaskId(task.getTaskId());
            log.setMessage("Failed to run task " + task.getTaskType());
            log.setException(Map.of(e.getClass().getSimpleName(), e.getMessage()));
            executorLogRepository.save(log);
            addStatusToQueue(TaskStatus.FAILED);
        }
    }

    private void addCompleteStatus(String outputObjectKey) {
        var taskStatus = CoordinatorProto.TaskStatus.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(TaskStatus.COMPLETE.toString())
                .setOutputObjectKey(outputObjectKey)
                .build();
        executorTaskQueue.addTaskStatus(taskStatus);
    }

    private void addStatusToQueue(TaskStatus status) {
        var taskStatus = CoordinatorProto.TaskStatus.newBuilder()
                .setTaskId(task.getTaskId())
                .setStatus(status.toString())
                .build();
        executorTaskQueue.addTaskStatus(taskStatus);
    }

    void withTask(CoordinatorProto.TaskAssignment assignment) {
        this.task = assignment;
    }
}
