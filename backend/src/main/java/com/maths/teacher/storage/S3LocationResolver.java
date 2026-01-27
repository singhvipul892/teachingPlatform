package com.maths.teacher.storage;

import com.maths.teacher.catalog.exception.ErrorMessages;
import java.net.URI;

public class S3LocationResolver {

    private S3LocationResolver() {
    }

    public static S3Location resolve(String storageUrl, S3Properties properties) {
        if (storageUrl == null || storageUrl.isBlank()) {
            throw new IllegalArgumentException(ErrorMessages.STORAGE_URL_REQUIRED);
        }
        if (storageUrl.startsWith("s3://")) {
            var withoutScheme = storageUrl.substring("s3://".length());
            var slashIndex = withoutScheme.indexOf('/');
            if (slashIndex <= 0) {
                throw new IllegalArgumentException(ErrorMessages.invalidS3Url(storageUrl));
            }
            var bucket = withoutScheme.substring(0, slashIndex);
            var key = withoutScheme.substring(slashIndex + 1);
            return new S3Location(bucket, key);
        }
        if (storageUrl.startsWith("https://")) {
            var uri = URI.create(storageUrl);
            var host = uri.getHost();
            if (host == null || !host.contains(".s3.")) {
                throw new IllegalArgumentException(ErrorMessages.unsupportedS3Url(storageUrl));
            }
            var bucket = host.substring(0, host.indexOf(".s3."));
            var key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
            return new S3Location(bucket, key);
        }
        var bucket = properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException(ErrorMessages.S3_BUCKET_REQUIRED);
        }
        return new S3Location(bucket, storageUrl);
    }

    public record S3Location(String bucket, String key) {}
}
