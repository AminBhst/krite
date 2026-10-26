package io.github.aminbhst.executor.runner;

import com.github.luben.zstd.ZstdInputStream;
import io.github.aminbhst.common.storage.StorageService;
import io.github.aminbhst.coordinator.CoordinatorProto;
import io.github.aminbhst.executor.persistence.repository.ExecutorLogRepository;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompressTaskRunnerTests {
    @Test
    void uploadsACompleteDecompressibleFrame() throws Exception {
        byte[] original = "A complete Zstandard frame. ".repeat(1000).getBytes();
        StorageService storage = mock(StorageService.class);
        when(storage.download("source")).thenReturn(new ByteArrayInputStream(original));
        when(storage.upload(any(), anyLong(), any())).thenAnswer(invocation -> {
            byte[] compressed = ((InputStream) invocation.getArgument(0)).readAllBytes();
            try (var decompressed = new ZstdInputStream(new ByteArrayInputStream(compressed))) {
                assertArrayEquals(original, decompressed.readAllBytes());
            }
            return "output";
        });
        var runner = new CompressTaskRunner(mock(ExecutorLogRepository.class), new ExecutorTaskQueue(), storage);
        runner.withTask(CoordinatorProto.TaskAssignment.newBuilder().setSourceFileObjectKey("source").build());
        assertEquals("output", runner.runInternal());
    }

    @Test
    void acknowledgingSentStatusPreservesANewerCompletion() {
        var queue = new ExecutorTaskQueue();
        queue.addTaskStatus(CoordinatorProto.TaskStatus.newBuilder().setTaskId(1).setStatus("IN_PROGRESS").build());
        var sent = queue.statusSnapshot();
        queue.addTaskStatus(CoordinatorProto.TaskStatus.newBuilder().setTaskId(1).setStatus("COMPLETE").build());
        queue.acknowledgeStatuses(sent);
        assertEquals("COMPLETE", queue.statusSnapshot().getFirst().getStatus());
    }
}
