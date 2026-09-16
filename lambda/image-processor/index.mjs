import {
    DeleteObjectCommand,
    GetObjectCommand,
    PutObjectCommand,
    S3Client,
} from "@aws-sdk/client-s3";

import sharp from "sharp";

const s3 = new S3Client({});

const INCOMING_PREFIX = "incoming/";
const OPTIMIZED_PREFIX = "optimized/";

const CACHE_CONTROL =
    "public, max-age=31536000, immutable";

export const handler = async (event) => {
    console.log("Received event:", JSON.stringify(event));

    for (const record of event.Records ?? []) {
        const bucket = record.s3.bucket.name;

        const sourceKey = decodeURIComponent(
            record.s3.object.key.replace(/\+/g, " "),
        );

        if (!sourceKey.startsWith(INCOMING_PREFIX)) {
            console.log(
                `Ignoring object outside incoming/: ${sourceKey}`,
            );
            continue;
        }

        if (!isSupportedImage(sourceKey)) {
            console.log(`Ignoring unsupported object: ${sourceKey}`);
            continue;
        }

        await processImage(bucket, sourceKey);
    }
};

async function processImage(bucket, sourceKey) {
    const destinationKey = createDestinationKey(sourceKey);
    const resizeOptions = getResizeOptions(sourceKey);

    if (!resizeOptions) {
        console.log(`Unknown image directory: ${sourceKey}`);
        return;
    }

    console.log(`Processing ${sourceKey} -> ${destinationKey}`);

    const getResponse = await s3.send(
        new GetObjectCommand({
            Bucket: bucket,
            Key: sourceKey,
        }),
    );

    if (!getResponse.Body) {
        throw new Error(`S3 returned no body for ${sourceKey}`);
    }

    const originalBytes = Buffer.from(
        await getResponse.Body.transformToByteArray(),
    );

    const webpBytes = await sharp(originalBytes, {
        failOn: "error",
        limitInputPixels: 40_000_000,
    })
        .rotate()
        .resize(resizeOptions)
        .webp({
            quality: 82,
            effort: 4,
        })
        .toBuffer();

    await s3.send(
        new PutObjectCommand({
            Bucket: bucket,
            Key: destinationKey,
            Body: webpBytes,
            ContentType: "image/webp",
            CacheControl: CACHE_CONTROL,
        }),
    );

    /*
     * Delete the temporary original only after the optimized
     * object was uploaded successfully.
     */
    await s3.send(
        new DeleteObjectCommand({
            Bucket: bucket,
            Key: sourceKey,
        }),
    );

    console.log(
        `Completed ${sourceKey} -> ${destinationKey}; ` +
        `${originalBytes.length} -> ${webpBytes.length} bytes`,
    );
}

export function createDestinationKey(sourceKey) {
    if (!sourceKey.startsWith(INCOMING_PREFIX)) {
        throw new Error(
            `Source key must begin with incoming/: ${sourceKey}`,
        );
    }

    const relativeKey = sourceKey.substring(
        INCOMING_PREFIX.length,
    );

    const withoutExtension = relativeKey.replace(
        /\.[^/.]+$/,
        "",
    );

    return `${OPTIMIZED_PREFIX}${withoutExtension}.webp`;
}

export function isSupportedImage(key) {
    return /\.(jpe?g|png|webp)$/i.test(key);
}

export function getResizeOptions(sourceKey) {
    if (
        sourceKey.startsWith(
            "incoming/seller-profiles/",
        )
    ) {
        return {
            width: 512,
            height: 512,
            fit: "cover",
            position: "centre",
            withoutEnlargement: true,
        };
    }

    if (sourceKey.startsWith("incoming/products/")) {
        return {
            width: 1200,
            height: 1200,
            fit: "inside",
            withoutEnlargement: true,
        };
    }

    return null;
}