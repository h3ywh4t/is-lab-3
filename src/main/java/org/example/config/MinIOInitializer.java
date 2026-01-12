package org.example.config;

import org.example.service.MinIOService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MinIOInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MinIOInitializer.class);

    @Autowired
    private MinIOService minIOService;

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Initializing MinIO bucket...");
            minIOService.ensureBucketExists();
            logger.info("MinIO bucket initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize MinIO bucket: {}", e.getMessage());
        }
    }
}