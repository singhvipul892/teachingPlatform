package com.maths.teacher.storage;

import com.maths.teacher.catalog.exception.ErrorMessages;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        if (properties.getRegion() == null || properties.getRegion().isBlank()) {
            throw new IllegalStateException(ErrorMessages.S3_REGION_REQUIRED);
        }
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean
    public S3Client s3Client(S3Properties properties) {
        if (properties.getRegion() == null || properties.getRegion().isBlank()) {
            throw new IllegalStateException(ErrorMessages.S3_REGION_REQUIRED);
        }
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }
}
