package com.microsoft.migration.assets.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "image_metadata")
public class ImageMetadata {
    @Id
    private String id;
    
    private String filename;
    private String contentType;
    private long size;
    private String s3Key;
    private String s3Url;
    
    private LocalDateTime uploadedAt;
    private LocalDateTime lastModified;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastModified = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}