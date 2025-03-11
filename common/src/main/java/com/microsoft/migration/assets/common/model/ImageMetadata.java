package com.microsoft.migration.assets.common.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class ImageMetadata {
    @Id
    private String id;
    private String filename;
    private String contentType;
    private Long size;
    private String s3Key;
    private String s3Url;
    private String thumbnailKey;
    private String thumbnailUrl;
}