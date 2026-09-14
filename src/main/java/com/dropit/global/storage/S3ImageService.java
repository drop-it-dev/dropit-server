package com.dropit.global.storage;

import com.dropit.global.exception.ServiceException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ImageService {

    private static final Duration URL_EXPIRATION = Duration.ofMinutes(10);

    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String upload(MultipartFile file, String directory) {
        validate(file);

        String contentType = file.getContentType();
        String extension = ALLOWED_IMAGE_TYPES.get(contentType);

        String key = directory
                + "/"
                + UUID.randomUUID()
                + extension;

        try {
            ObjectMetadata metadata = ObjectMetadata.builder()
                    .contentType(contentType)
                    .build();

            s3Template.upload(
                    bucket,
                    key,
                    file.getInputStream(),
                    metadata
            );

            return key;
        } catch (IOException | SdkException exception) {
            throw new ServiceException(
                    StorageErrorCode.FILE_UPLOAD_FAILED
            );
        }
    }

    public String createDownloadUrl(String key) {
        if (key == null) {
            return null;
        }

        try {
            return s3Template.createSignedGetURL(
                    bucket,
                    key,
                    URL_EXPIRATION
            ).toString();
        } catch (SdkException exception) {
            throw new ServiceException(
                    StorageErrorCode.FILE_URL_CREATION_FAILED
            );
        }
    }

    public String createPublicUrl(String key) {
        if (key == null) {
            return null;
        }

        try {
            S3Resource resource = s3Template.createResource(bucket, key);
            return resource.getURL().toString();
        } catch (IOException | SdkException exception) {
            throw new ServiceException(
                    StorageErrorCode.FILE_URL_CREATION_FAILED
            );
        }
    }

    public void deleteQuietly(String key) {
        if (key == null) {
            return;
        }

        try {
            s3Template.deleteObject(bucket, key);
        } catch (SdkException exception) {
            /*
             * The DB transaction has already succeeded.
             * Failing the HTTP request now would not restore that transaction,
             * so log the orphaned key for later cleanup.
             */
            log.warn(
                    "Failed to delete obsolete S3 image. key={}",
                    key,
                    exception
            );
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(
                    StorageErrorCode.EMPTY_FILE
            );
        }

        if (!ALLOWED_IMAGE_TYPES.containsKey(file.getContentType())) {
            throw new ServiceException(
                    StorageErrorCode.UNSUPPORTED_IMAGE_TYPE
            );
        }
    }
}
