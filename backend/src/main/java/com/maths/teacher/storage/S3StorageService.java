package com.maths.teacher.storage;

import com.maths.teacher.catalog.exception.ErrorMessages;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3StorageService(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public String uploadPdf(Long videoId, MultipartFile file) {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException(ErrorMessages.S3_BUCKET_REQUIRED);
        }
        var key = "videos/" + videoId + "/pdfs/" + UUID.randomUUID() + ".pdf";
        try {
            var putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType("application/pdf")
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ErrorMessages.FAILED_TO_READ_PDF_FILE, ex);
        }
        return "s3://" + properties.getBucket() + "/" + key;
    }

    public String uploadCourseThumbnail(Long courseId, MultipartFile file) {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException(ErrorMessages.S3_BUCKET_REQUIRED);
        }

        // Determine file extension from content type
        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        String extension = contentType.equals("image/png") ? "png" : "jpg";

        // If courseId is provided (update case), use it; otherwise generate unique filename
        String fileName = courseId != null ? "thumbnail." + extension : UUID.randomUUID() + "." + extension;
        var key = "courses/" + (courseId != null ? courseId : UUID.randomUUID()) + "/" + fileName;

        try {
            var putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read image file", ex);
        }

        return "s3://" + properties.getBucket() + "/" + key;
    }

    public void deleteByStorageUrl(String storageUrl) {
        var location = S3LocationResolver.resolve(storageUrl, properties);
        var deleteRequest = DeleteObjectRequest.builder()
                .bucket(location.bucket())
                .key(location.key())
                .build();
        s3Client.deleteObject(deleteRequest);
    }
}
