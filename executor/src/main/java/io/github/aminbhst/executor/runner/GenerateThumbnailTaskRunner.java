package io.github.aminbhst.executor.runner;

import io.github.aminbhst.common.core.task.TaskType;
import io.github.aminbhst.common.storage.StorageService;
import io.github.aminbhst.executor.persistence.repository.ExecutorLogRepository;
import io.github.aminbhst.executor.task.ExecutorTaskQueue;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GenerateThumbnailTaskRunner extends TaskRunner {

    private final StorageService storageService;

    protected GenerateThumbnailTaskRunner(ExecutorLogRepository executorLogRepository,
                                          ExecutorTaskQueue executorTaskQueue,
                                          StorageService storageService) {
        super(executorLogRepository, executorTaskQueue);
        this.storageService = storageService;
    }

    @Override
    public String getTaskType() {
        return TaskType.GENERATE_THUMBNAIL.toString();
    }

    @Override
    protected String runInternal() throws Exception {
        try (InputStream input = storageService.download(super.task.getSourceFileObjectKey())) {
            BufferedImage src = ImageIO.read(input);
            BufferedImage thumb = Scalr.resize(src, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(thumb, "png", out);
            String uid = storageService.upload(
                    new ByteArrayInputStream(out.toByteArray()),
                    out.size(),
                    "image/png"
            );
            log.info("Thumbnail {} generated", uid);
            return uid;
        }
    }

}
