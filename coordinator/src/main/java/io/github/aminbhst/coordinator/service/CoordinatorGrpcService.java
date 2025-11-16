package io.github.aminbhst.coordinator.service;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.common.persistence.repository.TaskRepository;
import io.github.aminbhst.coordinator.executor.ExecutorRegistry;
import io.github.aminbhst.coordinator.CoordinatorGrpc;
import io.github.aminbhst.coordinator.CoordinatorProto;
import io.grpc.stub.StreamObserver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoordinatorGrpcService extends CoordinatorGrpc.CoordinatorImplBase {

    private final ExecutorRegistry executorRegistry;

    private final TaskRepository taskRepository;

    @Override
    public void registerExecutor(CoordinatorProto.ExecutorInfo request, StreamObserver<CoordinatorProto.RegistrationResponse> responseObserver) {
        executorRegistry.addExecutor(request);
        var response = CoordinatorProto.RegistrationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Executor registered successfully: " + request.getExecutorId())
                .build();

        log.info("Executor {} registered successfully ", request.getExecutorId());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void sendHeartbeat(CoordinatorProto.ExecutorHeartbeat request, StreamObserver<CoordinatorProto.HeartbeatResponse> responseObserver) {
        executorRegistry.recalculateExecutorsWeights(request);
        responseObserver.onNext(
                CoordinatorProto.HeartbeatResponse.newBuilder()
                        .setAlive(true)
                        .build()
        );
        responseObserver.onCompleted();
    }

    @Override
    public void pollTasks(CoordinatorProto.TaskPollRequest request, StreamObserver<CoordinatorProto.TaskBatch> responseObserver) {
        int maxTasks = Math.max(1, request.getMaxTasks());
        var tasks = executorRegistry.pollTasks(request.getExecutorId(), maxTasks);
        var tasksBatch = tasks.stream().map(Task::toTaskAssignment).toList();
        var batch = CoordinatorProto.TaskBatch.newBuilder()
                .addAllTasks(tasksBatch)
                .build();

        for (var task : tasks) {
            task.setStatus(TaskStatus.IN_EXECUTOR_QUEUE);
            task.setAssignedNode(request.getExecutorId());
        }
        taskRepository.saveAll(tasks);

        responseObserver.onNext(batch);
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void sendTasksStatus(CoordinatorProto.TaskStatusBatch request, StreamObserver<CoordinatorProto.Empty> responseObserver) {
        for (CoordinatorProto.TaskStatus taskStatus : request.getStatusesList()) {
            Optional<Task> taskOptional = taskRepository.findById(taskStatus.getTaskId());
            if (taskOptional.isEmpty()) {
                log.error("Failed to find task {}", taskStatus.getTaskId());
                continue;
            }
            Task task = taskOptional.get();
            if (task.getStatus() != TaskStatus.COMPLETE) {
                task.setStatus(TaskStatus.valueOf(taskStatus.getStatus()));
                task.setOutputFileObjectKey(taskStatus.getOutputObjectKey());
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
        responseObserver.onNext(CoordinatorProto.Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
