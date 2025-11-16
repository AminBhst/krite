package io.github.aminbhst.common.persistence.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "executor_nodes")
public class ExecutorNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToMany(mappedBy = "assignedNode")
    private List<Task> tasks;

    @Column
    private int currentLoad;

    @Column
    private LocalDateTime lastHeartbeat;
}
