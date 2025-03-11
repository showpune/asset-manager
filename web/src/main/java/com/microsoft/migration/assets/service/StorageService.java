package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.S3Object;
import com.microsoft.migration.assets.common.util.StorageUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Interface for storage operations that can be implemented by different storage providers
 * (AWS S3, local file system, etc.)
 */
public interface StorageService {
    List<S3Object> listObjects();
    void uploadObject(MultipartFile file) throws IOException;
    InputStream getObject(String key) throws IOException;
    void deleteObject(String key) throws IOException;
    String getStorageType();

    /**
     * Get the thumbnail key for a given key
     */
    default String getThumbnailKey(String key) {
        return StorageUtil.getThumbnailKey(key);
    }
}