package com.dropit.global.storage;

/**
 * Represents the two S3 object keys involved in asynchronous image processing.
 *
 * sourceKey:
 *     Temporary original file uploaded by the Spring application.
 *
 * optimizedKey:
 *     WebP object that will be created asynchronously by Lambda.
 */
public record ImageUploadResult(
        String sourceKey,
        String optimizedKey
) {
}