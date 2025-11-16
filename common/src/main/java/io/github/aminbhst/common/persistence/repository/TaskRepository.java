package io.github.aminbhst.common.persistence.repository;

import io.github.aminbhst.common.persistence.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.status = 'INITIAL' " +
            "OR t.status = 'IN_COORDINATOR_QUEUE'" +
            " ORDER BY t.createdAt ASC")
    List<Task> findNextPendingTasks(Pageable pageable);

}