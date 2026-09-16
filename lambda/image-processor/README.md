# Dropit image processor

This AWS Lambda function processes images uploaded by the Dropit Spring Boot
application. It reads temporary objects from `incoming/`, resizes them, converts
them to WebP, writes them under `optimized/`, and deletes the temporary object
only after the optimized upload succeeds.

## Object-key contract

The Spring application uploads an original file using a key such as:

```text
incoming/products/100/550e8400-e29b-41d4-a716-446655440000.jpg
```

Lambda creates:

```text
optimized/products/100/550e8400-e29b-41d4-a716-446655440000.webp
```

Seller-profile images follow the same convention:

```text
incoming/seller-profiles/15/550e8400-e29b-41d4-a716-446655440000.png
optimized/seller-profiles/15/550e8400-e29b-41d4-a716-446655440000.webp
```

The database must store the `optimized/.../*.webp` key. The `incoming/` key is
temporary and must never be returned as the public image URL.

## Transformations

| Category | Output dimensions | Resize mode |
|---|---:|---|
| Seller profile | 512 x 512 | Cover and center-crop |
| Product | Maximum 1200 x 1200 | Fit inside and preserve aspect ratio |

All images are converted to WebP at quality 82. Images are not enlarged.
`sharp.rotate()` applies EXIF orientation before resizing.

## Required files

```text
lambda/image-processor/
├── index.mjs
├── index.test.mjs
├── package.json
├── package-lock.json
└── README.md
```

## Testable helpers in `index.mjs`

The unit tests import three pure helper functions that are already exported by
the canonical `index.mjs`:

```javascript
export function createDestinationKey(sourceKey) {
  if (!sourceKey.startsWith("incoming/")) {
    throw new Error(`Source key must begin with incoming/: ${sourceKey}`);
  }

  const relativeKey = sourceKey.substring("incoming/".length);
  const withoutExtension = relativeKey.replace(/\.[^/.]+$/, "");

  return `optimized/${withoutExtension}.webp`;
}

export function isSupportedImage(key) {
  return /\.(jpe?g|png|webp)$/i.test(key);
}

export function getResizeOptions(sourceKey) {
  if (sourceKey.startsWith("incoming/seller-profiles/")) {
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
```

`processImage()` uses the exported resize selection as follows:

```javascript
const resizeOptions = getResizeOptions(sourceKey);

if (!resizeOptions) {
  console.log(`Unknown image directory: ${sourceKey}`);
  return;
}

const webpBytes = await sharp(originalBytes, {
  failOn: "error",
  limitInputPixels: 40_000_000,
})
  .rotate()
  .resize(resizeOptions)
  .webp({ quality: 82, effort: 4 })
  .toBuffer();
```

## Package configuration

`package.json` should include:

```json
{
  "name": "dropit-image-processor",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "node --test index.test.mjs",
    "package:x64": "npm ci --omit=dev --os=linux --cpu=x64 && zip -r image-processor.zip index.mjs package.json package-lock.json node_modules",
    "package:arm64": "npm ci --omit=dev --os=linux --cpu=arm64 && zip -r image-processor.zip index.mjs package.json package-lock.json node_modules"
  },
  "dependencies": {
    "@aws-sdk/client-s3": "^3.0.0",
    "sharp": "^0.35.4"
  }
}
```

Generate and commit the lock file:

```bash
cd lambda/image-processor
npm install
```

## Run the unit tests

```bash
cd lambda/image-processor
npm test
```

The tests cover:

- Product destination-key generation.
- Seller-profile destination-key generation.
- Filenames containing multiple dots.
- Rejection of keys outside `incoming/`.
- Supported and unsupported extensions.
- Product resize settings.
- Seller-profile resize settings.
- Unknown input directories.

The tests deliberately focus on deterministic pure functions. An integration
test with LocalStack or an AWS test bucket should separately verify S3 download,
upload, metadata, and deletion behavior.

## Build for Lambda

Sharp contains native code. Install it for the same CPU architecture configured
for Lambda.

For x86-64:

```bash
cd lambda/image-processor
rm -rf node_modules image-processor.zip
npm ci --omit=dev --os=linux --cpu=x64
zip -r image-processor.zip index.mjs package.json package-lock.json node_modules
```

For ARM64:

```bash
cd lambda/image-processor
rm -rf node_modules image-processor.zip
npm ci --omit=dev --os=linux --cpu=arm64
zip -r image-processor.zip index.mjs package.json package-lock.json node_modules
```

Do not install Sharp for ARM64 and configure Lambda as `x86_64`, or vice versa.

## Deploy with AWS SAM

The root `template.yaml` deliberately does not create the S3 bucket. Dropit
already has an existing bucket, and attempting to recreate it through the stack
would fail.

Build and deploy:

```bash
sam build
sam deploy --guided
```

Suggested guided-deployment values:

```text
Stack name: dropit-image-processing
Region: ap-northeast-2
ImageBucketName: dropit-s3-images
LambdaArchitecture: x86_64
AlertEmail: your-email@example.com
```

If an alert email is supplied, AWS sends a subscription-confirmation email.
Confirm the subscription before expecting CloudWatch alarm emails.

## Attach the existing S3 bucket trigger

CloudFormation/SAM cannot attach a standard SAM `S3` event to an arbitrary
pre-existing bucket without additional custom-resource machinery. Therefore,
`template.yaml` creates the Lambda invocation permission and outputs the exact
AWS CLI command for attaching the notification.

After deploying, open the CloudFormation stack's **Outputs** tab and run the
`ConfigureExistingBucketCommand` value.

Before running it, inspect the bucket's existing notification configuration:

```bash
aws s3api get-bucket-notification-configuration \
  --bucket dropit-s3-images
```

Important: `put-bucket-notification-configuration` replaces the complete bucket
notification configuration. If the bucket already has unrelated SQS, SNS, or
Lambda notifications, merge them into the JSON rather than replacing them.

The required notification is conceptually:

```json
{
  "LambdaFunctionConfigurations": [
    {
      "Id": "dropit-image-processor",
      "LambdaFunctionArn": "LAMBDA_FUNCTION_ARN",
      "Events": ["s3:ObjectCreated:*"] ,
      "Filter": {
        "Key": {
          "FilterRules": [
            {
              "Name": "prefix",
              "Value": "incoming/"
            }
          ]
        }
      }
    }
  ]
}
```

Only `incoming/` triggers the function. Lambda writes to `optimized/`, which
prevents a recursive invocation loop.

## IAM access

The SAM template grants only the required object-level access:

- `s3:GetObject` for `incoming/*`.
- `s3:DeleteObject` for `incoming/*`.
- `s3:PutObject` for `optimized/*`.
- `sqs:SendMessage` for the failure queue.
- Standard CloudWatch Logs permissions.

If the bucket uses a customer-managed KMS key, add the corresponding KMS
permissions to the function role.

## Test the deployed function

Upload a JPEG manually:

```bash
aws s3 cp test.jpg \
  s3://dropit-s3-images/incoming/products/100/manual-test.jpg \
  --content-type image/jpeg
```

Check the optimized object:

```bash
aws s3api head-object \
  --bucket dropit-s3-images \
  --key optimized/products/100/manual-test.webp
```

The result should include:

```text
ContentType: image/webp
CacheControl: public, max-age=31536000, immutable
```

The original object should have been deleted:

```bash
aws s3api head-object \
  --bucket dropit-s3-images \
  --key incoming/products/100/manual-test.jpg
```

The last command should return a not-found error after successful processing.

## Asynchronous behavior

Spring stores the future `optimized/.../*.webp` key before Lambda has necessarily
finished creating it. The CloudFront URL can therefore return a temporary 404
immediately after an upload.

Configure CloudFront's error-caching minimum TTL for 404 responses to zero or a
very small value. The frontend should display a processing placeholder and retry
the image after a short delay. A later version can add an explicit
`PROCESSING/READY/FAILED` status to the database.

## Operational safeguards

The SAM template configures:

- Two asynchronous Lambda retries.
- An encrypted SQS failure queue.
- A Lambda error alarm.
- An alarm when the failure queue contains messages.
- Optional SNS email notifications.

Also configure an S3 lifecycle rule outside this template to delete abandoned
objects under `incoming/` after one day. Do not apply that rule to `optimized/`.
