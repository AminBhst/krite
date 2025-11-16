package io.github.aminbhst.executor.job;

import com.aminbhst.quartzautoconfigboot.annotation.QuartzJob;
import io.github.aminbhst.executor.client.CoordinatorClient;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@QuartzJob(repeatInterval = 5000)

public class TaskPollJob {

    private static final int MAX_QUEUE_TASKS = 50;

    private final ExecutorTaskQueue taskQueue;

    private final CoordinatorClient coordinatorClient;

    @Scheduled(fixedDelay = 5000)
    public void execute() {
        try {
            log.info("Executing TaskPollJob");
            int queueSize = taskQueue.size();
            if (queueSize >= MAX_QUEUE_TASKS) {
                return;
            }
            int maxTasks = MAX_QUEUE_TASKS - queueSize;
            var taskBatch = coordinatorClient.pollTask(maxTasks);
            taskBatch.getTasksList().forEach(taskQueue::pushTask);
        } catch (Exception ex) {
            log.error("Job error", ex);
        }
    }
}
