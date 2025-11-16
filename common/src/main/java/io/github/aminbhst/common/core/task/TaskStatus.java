package io.github.aminbhst.common.core.task;

public enum TaskStatus {
    INITIAL,
    IN_QUEUE,
    IN_PROGRESS,
    COMPLETE,
    IN_COORDINATOR_QUEUE,
    IN_EXECUTOR_QUEUE,
    FAILED
}
