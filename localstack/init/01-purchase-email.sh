#!/bin/sh
set -eu

REGION="ap-northeast-2"
ORDER_DLQ_NAME="dropit-order-dlq"
ORDER_QUEUE_NAME="dropit-order-queue"
DLQ_NAME="dropit-purchase-email-dlq"
QUEUE_NAME="dropit-purchase-email"
FROM_ADDRESS="no-reply@dropit.local"

# The existing notification listener is always active, so its local queue
# must exist when the whole Spring application starts with LocalStack.
awslocal sqs create-queue \
  --region "$REGION" \
  --queue-name "dropit-notification" \
  >/dev/null

ORDER_DLQ_URL=$(awslocal sqs create-queue \
  --region "$REGION" \
  --queue-name "$ORDER_DLQ_NAME" \
  --attributes MessageRetentionPeriod=1209600 \
  --query QueueUrl \
  --output text)

ORDER_DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --region "$REGION" \
  --queue-url "$ORDER_DLQ_URL" \
  --attribute-names QueueArn \
  --query Attributes.QueueArn \
  --output text)

ORDER_QUEUE_URL=$(awslocal sqs create-queue \
  --region "$REGION" \
  --queue-name "$ORDER_QUEUE_NAME" \
  --attributes VisibilityTimeout=60,ReceiveMessageWaitTimeSeconds=10 \
  --query QueueUrl \
  --output text)

ORDER_REDRIVE_ATTRIBUTES=$(printf \
  '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}"}' \
  "$ORDER_DLQ_ARN")

awslocal sqs set-queue-attributes \
  --region "$REGION" \
  --queue-url "$ORDER_QUEUE_URL" \
  --attributes "$ORDER_REDRIVE_ATTRIBUTES"

DLQ_URL=$(awslocal sqs create-queue \
  --region "$REGION" \
  --queue-name "$DLQ_NAME" \
  --attributes MessageRetentionPeriod=1209600 \
  --query QueueUrl \
  --output text)

DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --region "$REGION" \
  --queue-url "$DLQ_URL" \
  --attribute-names QueueArn \
  --query Attributes.QueueArn \
  --output text)

QUEUE_URL=$(awslocal sqs create-queue \
  --region "$REGION" \
  --queue-name "$QUEUE_NAME" \
  --attributes VisibilityTimeout=60,ReceiveMessageWaitTimeSeconds=10 \
  --query QueueUrl \
  --output text)

REDRIVE_ATTRIBUTES=$(printf \
  '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}"}' \
  "$DLQ_ARN")

awslocal sqs set-queue-attributes \
  --region "$REGION" \
  --queue-url "$QUEUE_URL" \
  --attributes "$REDRIVE_ATTRIBUTES"

awslocal ses verify-email-identity \
  --region "$REGION" \
  --email-address "$FROM_ADDRESS"

echo "Local order/email SQS queues, DLQs and SES identity are ready."
