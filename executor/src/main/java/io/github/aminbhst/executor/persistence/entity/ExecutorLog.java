package io.github.aminbhst.executor.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("executor_logs")
public class ExecutorLog {

    @Id
    private String id;

    private Instant timestamp;
    private String executorId;
    private Long taskId;
    private String taskType;
    private String message;

    private Map<String, Object> details;
    private Map<String, Object> exception;
}
