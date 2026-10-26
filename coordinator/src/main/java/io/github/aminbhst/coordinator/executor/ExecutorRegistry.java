package io.github.aminbhst.coordinator.executor;

import io.github.aminbhst.common.persistence.entity.Task;
import io.github.aminbhst.coordinator.CoordinatorProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ExecutorRegistry {

    private final Map<String, CoordinatorProto.ExecutorInfo> executors = new ConcurrentHashMap<>();
    private final Map<String, BlockingDeque<Task>> executorTasks = new ConcurrentHashMap<>();

    private volatile List<String> executorsWeighted = List.of();
    private final Map<String, Long> heartbeats = new ConcurrentHashMap<>();

    private final AtomicInteger rrIndex = new AtomicInteger(0);

    public void recalculateExecutorsWeights(CoordinatorProto.ExecutorHeartbeat heartbeat) {
        CoordinatorProto.ExecutorInfo executorInfo = executors.get(heartbeat.getId());
        if (executorInfo == null) {
            log.warn("No executor available for heartbeat! executor id: {}", heartbeat.getId());
            return;
        }
        CoordinatorProto.ExecutorInfo.Builder newInfo = CoordinatorProto.ExecutorInfo.newBuilder(executorInfo)
                .setCpuLoad(heartbeat.getCpuLoad())
                .setMemoryFree(heartbeat.getMemoryFree());
        executors.put(heartbeat.getId(), newInfo.build());
        heartbeats.put(heartbeat.getId(), System.currentTimeMillis());
        executorTasks.computeIfAbsent(heartbeat.getId(), k -> new LinkedBlockingDeque<>());
        rebuildWeights();
    }

    public void addExecutor(CoordinatorProto.ExecutorInfo executor) {
        executors.put(executor.getExecutorId(), executor);
        heartbeats.put(executor.getExecutorId(), System.currentTimeMillis());
        executorTasks.computeIfAbsent(executor.getExecutorId(), k -> new LinkedBlockingDeque<>());
        rebuildWeights();
    }

    public void removeExecutor(String nodeId) {
        executors.remove(nodeId);
        heartbeats.remove(nodeId);
        executorTasks.remove(nodeId);
        rebuildWeights();
    }

    /**
     * Assigns a new task to the next executor based on weighted RR.
     */
    public String pushTask(Task task) {
        rebuildWeights();
        if (executorsWeighted.isEmpty()) {
            System.err.println("No executors available to assign task: " + task.getId());
            return null;
        }

        List<String> weights = executorsWeighted;
        if (weights.isEmpty()) return null;
        String nextId = weights.get(Math.floorMod(rrIndex.getAndIncrement(), weights.size()));
        BlockingDeque<Task> queue = executorTasks.get(nextId);
        if (queue != null) {
            queue.offer(task);
        }
        return nextId;
    }

    public Task pollTask(String executorId) {
        BlockingDeque<Task> queue = executorTasks.get(executorId);
        return (queue != null) ? queue.poll() : null;
    }

    public List<Task> pollTasks(String executorId, int maxCount) {
        BlockingDeque<Task> queue = executorTasks.get(executorId);
        if (queue == null) {
            return List.of();
        }

        List<Task> batch = new ArrayList<>(maxCount);

        for (int i = 0; i < maxCount; i++) {
            Task task = queue.poll();
            if (task == null) break;
//            var assignment = CoordinatorProto.TaskAssignment.newBuilder()
//                    .setTaskId(task.getId().toString())
//                    .setSourceFileObjectKey(task.getSourceFileObjectKey())
//                    .setTaskType(String.valueOf(task.getType()))
//                    .build();
            batch.add(task);
        }

        return batch;
    }

    /**
     * Weighted Round Robin scheduler logic.
     */
    /**
     * Recomputes weights and rebuilds the expanded executor list.
     */
    private synchronized void rebuildWeights() {
        if (executors.isEmpty()) {
            executorsWeighted = List.of();
            return;
        }

        int maxCores = executors.values().stream()
                .mapToInt(CoordinatorProto.ExecutorInfo::getCpuCores)
                .max().orElse(1);

        long maxMemory = executors.values().stream()
                .mapToLong(CoordinatorProto.ExecutorInfo::getMemoryTotal)
                .max().orElse(1);

        Map<String, Integer> weights = computeWeights(maxCores, maxMemory);

        List<String> newWeights = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            if (System.currentTimeMillis() - heartbeats.getOrDefault(entry.getKey(), 0L) > 10_000) continue;
            for (int i = 0; i < entry.getValue(); i++) {
                newWeights.add(entry.getKey());
            }
        }

        Collections.shuffle(newWeights);
        executorsWeighted = List.copyOf(newWeights);
    }

    private Map<String, Integer> computeWeights(int maxCores, long maxMemory) {
        double wCores = 0.3;
        double wMemory = 0.4;
        double wCpuLoad = 0.3;

        Map<String, Integer> weights = new HashMap<>();

        for (CoordinatorProto.ExecutorInfo e : executors.values()) {
            double coresScore = (double) e.getCpuCores() / Math.max(1, maxCores);
            double memoryScore = (double) e.getMemoryFree() / Math.max(1, maxMemory);
            double cpuScore = 1.0 - Math.clamp(e.getCpuLoad(), 0.0, 1.0);

            double totalScore = wCores * coresScore + wMemory * memoryScore + wCpuLoad * cpuScore;

            int weight = Math.max(1, (int) Math.round(totalScore * 10));
            weights.put(e.getExecutorId(), weight);
        }
        return weights;
    }

    public boolean taskNotInQueue(Task task) {
        return executorTasks
                .values()
                .stream()
                .noneMatch(tasks -> tasks.stream().anyMatch(t -> t.getId().equals(task.getId())));
    }

    public boolean isEmpty() {
        return executors.isEmpty();
    }

    public boolean contains(String id) {
        return executors.containsKey(id);
    }
}
