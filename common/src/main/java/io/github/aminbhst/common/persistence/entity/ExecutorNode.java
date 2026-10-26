package io.github.aminbhst.common.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "executor_nodes")
public class ExecutorNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String executorId;

    private String hostname;
    private int cpuCores;
    private double cpuLoad;
    private long memoryTotal;
    private long memoryFree;

    @Column
    private int currentLoad;

    @Column
    private LocalDateTime lastHeartbeat;
}
