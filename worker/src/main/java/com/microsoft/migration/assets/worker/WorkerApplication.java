package com.microsoft.migration.assets.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.microsoft.migration.assets.worker",
    "com.microsoft.migration.assets.common"
})
@EntityScan({
    "com.microsoft.migration.assets.worker.model",
    "com.microsoft.migration.assets.common.model"
})
@EnableJpaRepositories({
    "com.microsoft.migration.assets.worker.repository",
    "com.microsoft.migration.assets.common.repository"
})
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WorkerApplication.class);
        application.addListeners(new ApplicationPidFileWriter());
        application.run(args);
    }
}