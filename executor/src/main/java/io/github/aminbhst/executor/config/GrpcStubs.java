package io.github.aminbhst.executor.config;

import io.github.aminbhst.coordinator.CoordinatorGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

@Component
public class GrpcStubs {

    @Bean
    CoordinatorGrpc.CoordinatorBlockingStub coordinatorBlockingStub(GrpcChannelFactory channels) {
        return CoordinatorGrpc.newBlockingStub(channels.createChannel("local"));
    }

}
