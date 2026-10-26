package io.github.aminbhst.coordinator.service;

import io.github.aminbhst.common.persistence.entity.ExecutorNode;
import io.github.aminbhst.common.persistence.repository.ExecutorNodeRepository;
import io.github.aminbhst.coordinator.CoordinatorProto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExecutorTelemetry {
    private final ExecutorNodeRepository repository;

    @Transactional
    public void register(CoordinatorProto.ExecutorInfo info) {
        ExecutorNode node = repository.findByExecutorId(info.getExecutorId()).orElseGet(ExecutorNode::new);
        node.setExecutorId(info.getExecutorId());
        node.setHostname(info.getHostname());
        node.setCpuCores(info.getCpuCores());
        node.setCpuLoad(info.getCpuLoad());
        node.setMemoryTotal(info.getMemoryTotal());
        node.setMemoryFree(info.getMemoryFree());
        node.setLastHeartbeat(LocalDateTime.now());
        repository.save(node);
    }

    @Transactional
    public void heartbeat(CoordinatorProto.ExecutorHeartbeat heartbeat) {
        repository.findByExecutorId(heartbeat.getId()).ifPresent(node -> {
            node.setCpuLoad(heartbeat.getCpuLoad());
            node.setMemoryFree(heartbeat.getMemoryFree());
            node.setLastHeartbeat(LocalDateTime.now());
            repository.save(node);
        });
    }
}
