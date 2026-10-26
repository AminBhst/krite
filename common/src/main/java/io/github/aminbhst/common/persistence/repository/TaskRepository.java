package io.github.aminbhst.common.persistence.repository;

import io.github.aminbhst.common.persistence.entity.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;
import io.github.aminbhst.common.core.task.TaskStatus;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    interface StatusCount {
        TaskStatus getStatus();
        long getTotal();
    }

    @Query("select t.status as status, count(t) as total from Task t " +
            "where (:username is null or t.createdBy.username = :username) group by t.status")
    List<StatusCount> countByStatus(String username);

    @Query("select t.createdAt from Task t where t.createdAt >= :since " +
            "and (:username is null or t.createdBy.username = :username)")
    List<LocalDateTime> submissionTimes(String username, LocalDateTime since);

    long countByAssignedNodeAndStatusIn(String assignedNode, List<TaskStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.status = 'INITIAL' " +
            "OR t.status = 'IN_COORDINATOR_QUEUE'" +
            " ORDER BY t.createdAt ASC")
    List<Task> findNextPendingTasks(Pageable pageable);

}
