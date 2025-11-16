package io.github.aminbhst.common.persistence.entity;

import io.github.aminbhst.common.core.task.TaskStatus;
import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.coordinator.CoordinatorProto;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TaskType type;

    @Column(nullable = false)
    private String sourceFileObjectKey;

    private String outputFileObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.INITIAL;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String assignedNode;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column
    private LocalDateTime updatedAt;

    public CoordinatorProto.TaskAssignment toTaskAssignment() {
        return CoordinatorProto.TaskAssignment.newBuilder()
                .setTaskId(this.getId())
                .setSourceFileObjectKey(this.getSourceFileObjectKey())
                .setTaskType(String.valueOf(this.getType()))
                .build();
    }

}
