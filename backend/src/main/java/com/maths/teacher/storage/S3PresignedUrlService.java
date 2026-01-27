package com.maths.teacher.storage;

import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3PresignedUrlService(S3Presigner s3Presigner, S3Properties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public PresignedUrlResult createPresignedDownloadUrl(String storageUrl) {
        var location = S3LocationResolver.resolve(storageUrl, properties);
        var request = GetObjectRequest.builder()
                .bucket(location.bucket())
                .key(location.key())
                .build();

        var expiryDuration = Duration.ofMinutes(properties.getPresignExpiryMinutes());
        var presignedRequest = s3Presigner.presignGetObject(builder -> builder
                .signatureDuration(expiryDuration)
                .getObjectRequest(request));

        return new PresignedUrlResult(
                presignedRequest.url().toString(),
                (int) expiryDuration.getSeconds()
        );
    }

    public record PresignedUrlResult(String url, int expiresInSeconds) {}
}
