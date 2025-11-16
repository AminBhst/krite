package io.github.aminbhst.executor.job;

import io.github.aminbhst.executor.client.CoordinatorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutorStartupRunner implements CommandLineRunner {

    private final CoordinatorClient coordinatorClient;

    public ExecutorStartupRunner(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Override
    public void run(String... args) {
        try {
            var response = coordinatorClient.registerExecutor();
            if (!response.getSuccess()) {
                log.error("Failed to register executor via the coordinator!");
                System.exit(1);
            }
        } catch (Exception e) {
            log.error("Failed to register executor!");
            System.exit(1);
        }
    }
}
