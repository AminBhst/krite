package io.github.aminbhst.executor;

import io.github.aminbhst.coordinator.CoordinatorProto;
import io.github.aminbhst.executor.client.CoordinatorClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Component
class ExecutorApplicationTests {

    private final CoordinatorClient coordinatorClient;

    @Autowired
    ExecutorApplicationTests(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    @Test
    void testCoordinatorGrpcCommunication() {
        CoordinatorProto.RegistrationResponse response = coordinatorClient.registerExecutor();
        assertTrue(response.getSuccess(), "Expected registration to succeed");
    }

}
