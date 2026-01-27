package com.maths.teacher.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public class S3Properties {

    private String region;
    private String bucket;
    private int presignExpiryMinutes = 10;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getPresignExpiryMinutes() {
        return presignExpiryMinutes;
    }

    public void setPresignExpiryMinutes(int presignExpiryMinutes) {
        this.presignExpiryMinutes = presignExpiryMinutes;
    }
}
