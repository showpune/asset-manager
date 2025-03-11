package com.microsoft.migration.assets.common.repository;

import com.microsoft.migration.assets.common.model.ImageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageMetadataRepository extends JpaRepository<ImageMetadata, String> {
}