package com.microsoft.migration.assets.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Scheduled(fixedDelay = 60000) // Run every minute
    public void processFiles() {
        log.info("Starting file processing job");
        
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();
                
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        
        for (S3Object object : response.contents()) {
            log.info("Found file: {} (size: {} bytes)", 
                    object.key(), 
                    object.size());
        }
        
        log.info("Completed file processing job");
    }
}