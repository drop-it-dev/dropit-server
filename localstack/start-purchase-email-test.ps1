$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env.local"

if (-not (Test-Path $envFile)) {
    throw ".env.local is missing. Copy .env.example and fill in local values first."
}

Push-Location $projectRoot
try {
    Write-Host "[1/3] Starting MySQL and Redis."
    docker compose --env-file .env.local up -d --wait db redis
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start MySQL or Redis."
    }

    Write-Host "[2/3] Preparing the isolated dropit_localstack database."
    $dbContainer = docker compose --env-file .env.local ps -q db
    if ([string]::IsNullOrWhiteSpace($dbContainer)) {
        throw "MySQL container was not found."
    }

    $dbUser = docker exec $dbContainer printenv MYSQL_USER
    $dbPassword = docker exec $dbContainer printenv MYSQL_PASSWORD
    docker exec -e MYSQL_PWD=$dbPassword $dbContainer mysql -u $dbUser -D dropit_localstack -e "SELECT 1;" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "The isolated database is missing. Creating it once with the root account."
        $rootPassword = docker exec $dbContainer printenv MYSQL_ROOT_PASSWORD
        $createSql = "CREATE DATABASE IF NOT EXISTS dropit_localstack CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON dropit_localstack.* TO '$dbUser'@'%'; FLUSH PRIVILEGES;"
        docker exec -e MYSQL_PWD=$rootPassword $dbContainer mysql -u root -e $createSql
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to prepare dropit_localstack. Check whether the existing MySQL volume and .env.local use the same root password."
        }
    }

    Write-Host "[3/3] Starting LocalStack with order/email SQS, DLQ and SES."
    docker compose --env-file .env.local --profile local-aws up -d --wait localstack
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start LocalStack."
    }

    Write-Host ""
    Write-Host "Ready"
    Write-Host "IntelliJ active profiles: local,localstack"
    Write-Host "Postman collection: postman/dropit-purchase-email-local.postman_collection.json"
    Write-Host "Captured email inbox: http://localhost:4566/_aws/ses"
}
finally {
    Pop-Location
}
