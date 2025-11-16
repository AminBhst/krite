package io.github.aminbhst.common.storage;

import java.io.InputStream;

public interface StorageService {
    String upload(InputStream inputStream, long size, String contentType);

    InputStream download(String id) throws Exception;
}
