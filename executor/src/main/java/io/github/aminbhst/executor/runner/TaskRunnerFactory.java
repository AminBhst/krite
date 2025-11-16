package io.github.aminbhst.executor.runner;

import io.github.aminbhst.coordinator.CoordinatorProto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TaskRunnerFactory {

    private final List<TaskRunner> runners;
    private final ApplicationContext ctx;

    private final Map<String, Class<? extends TaskRunner>> registry = new HashMap<>();

    @PostConstruct
    void init() {
        for (TaskRunner r : runners) {
            registry.put(r.getTaskType(), r.getClass());
        }
    }

    public TaskRunner create(CoordinatorProto.TaskAssignment task) {
        Class<? extends TaskRunner> runnerClass = registry.get(task.getTaskType());
        if (runnerClass == null) {
            throw new IllegalArgumentException("Unknown task type: " + task.getTaskType());
        }
        TaskRunner runner = ctx.getBean(runnerClass);
        runner.withTask(task);
        return runner;
    }
}
