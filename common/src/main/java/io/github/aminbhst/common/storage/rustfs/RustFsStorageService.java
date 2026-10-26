package io.github.aminbhst.common.storage.rustfs;

import io.github.aminbhst.common.storage.StorageService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.UUID;

@Service
public class RustFsStorageService implements StorageService {

    private final S3Client rustFsClient;
    private final String bucketName;

    public RustFsStorageService(S3Client rustFsClient, RustFsConfig config) {
        this.rustFsClient = rustFsClient;
        this.bucketName = config.getBucket();
    }

    @Override
    public String upload(InputStream inputStream, long size, String contentType) {
        try (InputStream input = inputStream) {
            String objectName = UUID.randomUUID().toString();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();

            rustFsClient.putObject(request, RequestBody.fromInputStream(input, size));
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("File upload failed", e);
        }
    }

    @Override
    public InputStream download(String id) {
        ResponseInputStream<GetObjectResponse> response = rustFsClient.getObject(
                GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(id)
                        .build()
        );
        return response;
    }
}
