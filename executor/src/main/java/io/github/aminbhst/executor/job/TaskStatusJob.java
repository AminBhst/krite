package io.github.aminbhst.executor.job;

import io.github.aminbhst.executor.client.CoordinatorClient;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskStatusJob {

    private final CoordinatorClient coordinatorClient;

    private final ExecutorTaskQueue executorTaskQueue;

    @Scheduled(fixedDelay = 1000)
    public void execute() {
        coordinatorClient.sendTaskStatuses(executorTaskQueue.taskStatuses);
        executorTaskQueue.taskStatuses.clear();
    }
}
