package io.github.aminbhst.executor.job;

import com.aminbhst.quartzautoconfigboot.annotation.QuartzJob;
import io.github.aminbhst.executor.client.CoordinatorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatJob {

    private static final int MAX_HEARTBEAT_FAILURE = 3;
    private int heartbeatFailureCount = 0;

    private final CoordinatorClient coordinatorClient;

    @Scheduled(fixedDelay = 1000)
    public void execute() {
        log.info("HeartbeatJob executing");
        try {
            coordinatorClient.sendHeartbeat();
            log.info("Sent heartbeat to coordinator");
        } catch (Exception e) {
            heartbeatFailureCount++;
            log.error("Failed to send heartbeat", e);
            if (heartbeatFailureCount >= MAX_HEARTBEAT_FAILURE) {
                log.error("Heartbeat failed 3 times! Shutting down the executor...");
                System.exit(1);
            }
        }

    }
}
