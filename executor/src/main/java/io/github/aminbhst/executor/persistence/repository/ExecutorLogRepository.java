package io.github.aminbhst.executor.persistence.repository;

import io.github.aminbhst.executor.persistence.entity.ExecutorLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExecutorLogRepository extends MongoRepository<ExecutorLog, String> {
}
