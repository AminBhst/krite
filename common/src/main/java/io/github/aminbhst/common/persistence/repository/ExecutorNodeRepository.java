package io.github.aminbhst.common.persistence.repository;

import io.github.aminbhst.common.persistence.entity.ExecutorNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ExecutorNodeRepository extends JpaRepository<ExecutorNode, UUID> {
    Optional<ExecutorNode> findByExecutorId(String executorId);
}
