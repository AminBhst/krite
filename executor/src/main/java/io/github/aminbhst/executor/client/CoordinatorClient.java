package io.github.aminbhst.executor.client;

import io.github.aminbhst.coordinator.CoordinatorGrpc;
import io.github.aminbhst.coordinator.CoordinatorProto;
import io.github.aminbhst.executor.config.ExecutorConfig;
import io.github.aminbhst.executor.util.SystemMonitor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CoordinatorClient {

    private final CoordinatorGrpc.CoordinatorBlockingStub stub;

    private final SystemMonitor systemMonitor;

    private final ExecutorConfig executorConfig;

    public CoordinatorProto.TaskBatch pollTask(int maxTasks) {
        var request = CoordinatorProto.TaskPollRequest.newBuilder()
                .setExecutorId(executorConfig.getId())
                .setMaxTasks(maxTasks)
                .build();

        return stub.pollTasks(request);
    }

    public CoordinatorProto.RegistrationResponse registerExecutor() {
        var request = CoordinatorProto.ExecutorInfo.newBuilder()
                .setExecutorId(executorConfig.getId())
                .setHostname(executorConfig.getId())
                .setCpuLoad(systemMonitor.getCpuLoad())
                .setCpuCores(systemMonitor.getPhysicalCores())
                .setMemoryTotal(systemMonitor.getTotalMemory())
                .setMemoryFree(systemMonitor.getFreeMemory())
                .build();

        return stub.registerExecutor(request);
    }

    public CoordinatorProto.HeartbeatResponse sendHeartbeat() {
        var heartbeat = CoordinatorProto.ExecutorHeartbeat.newBuilder()
                .setId(executorConfig.getId())
                .setCpuLoad(systemMonitor.getCpuLoad())
                .setMemoryFree(systemMonitor.getFreeMemory())
                .build();

        return stub.sendHeartbeat(heartbeat);
    }

    public void sendTaskStatuses(List<CoordinatorProto.TaskStatus> statuses) {
        var response = stub.sendTasksStatus(
                CoordinatorProto.TaskStatusBatch.newBuilder().addAllStatuses(statuses).build()
        );
    }
}
