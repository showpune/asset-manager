package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.S3Object;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Interface for storage operations that can be implemented by different storage providers
 * (AWS S3, local file system, etc.)
 */
public interface StorageService {
    
    /**
     * List all objects in storage
     */
    List<S3Object> listObjects();
    
    /**
     * Upload file to storage
     */
    void uploadObject(MultipartFile file) throws IOException;
    
    /**
     * Get object from storage by key
     */
    InputStream getObject(String key) throws IOException;
    
    /**
     * Delete object from storage by key
     */
    void deleteObject(String key) throws IOException;
}