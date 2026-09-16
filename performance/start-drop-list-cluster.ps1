param(
    [string]$JarPath = 'tmp\dropit-server-performance.jar',
    [ValidateSet('200ms', '500ms', '1s')]
    [string]$RedisCommandTimeout = '200ms',
    [ValidateSet('true', 'false')]
    [string]$ListRedisCacheEnabled = 'true',
    [ValidateSet('true', 'false')]
    [string]$ListLocalCacheEnabled = 'true',
    [ValidateSet('768m', '1g')]
    [string]$SpringMemory = '768m',
    [ValidateSet('512m', '768m')]
    [string]$JavaHeap = '512m'
)

$ErrorActionPreference = 'Stop'

$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDirectory = Split-Path -Parent $performanceDirectory
$environmentFile = Join-Path $projectDirectory '.env.local'
$composeFile = Join-Path $projectDirectory 'compose.yml'
$performanceComposeFile = Join-Path $performanceDirectory 'compose.performance.yml'
$nginxConfig = Join-Path $performanceDirectory 'nginx\drop-list-cluster.conf'

if (-not [System.IO.Path]::IsPathRooted($JarPath)) {
    $JarPath = Join-Path $projectDirectory $JarPath
}

foreach ($requiredFile in @($environmentFile, $composeFile, $performanceComposeFile, $nginxConfig, $JarPath)) {
    if (-not (Test-Path -LiteralPath $requiredFile)) {
        throw "Required file is missing: $requiredFile"
    }
}

docker compose --file $composeFile --env-file $environmentFile up --detach db redis | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'MySQL or Redis could not be started.'
}

$performanceEnvironmentFile = Join-Path $projectDirectory '.env.performance.local'
docker compose --file $performanceComposeFile --env-file $performanceEnvironmentFile up --detach influxdb prometheus grafana | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'InfluxDB, Prometheus, or Grafana could not be started.'
}

$containersToReplace = @(
    'dropit-list-gateway',
    'dropit-list-app-1',
    'dropit-list-app-2',
    'dropit-performance-gateway',
    'dropit-performance-app-1',
    'dropit-performance-app-2',
    'dropit-performance-app-3',
    'dropit-performance-app-4'
)

foreach ($containerName in $containersToReplace) {
    $existing = docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}'
    if ($existing) {
        docker rm --force $containerName | Out-Null
    }
}

for ($index = 1; $index -le 2; $index++) {
    $containerName = "dropit-list-app-$index"

    docker run `
        --detach `
        --name $containerName `
        --network dropit_default `
        --memory $SpringMemory `
        --env-file $environmentFile `
        --env DB_HOST=db `
        --env DB_PORT=3306 `
        --env REDIS_HOST=redis `
        --env REDIS_PORT=6379 `
        --env "REDIS_COMMAND_TIMEOUT=$RedisCommandTimeout" `
        --env "app.drop.list.redis-cache.enabled=$ListRedisCacheEnabled" `
        --env "app.drop.list.local-cache.enabled=$ListLocalCacheEnabled" `
        --env SPRING_PROFILES_ACTIVE=performance `
        --env SPRING_JPA_HIBERNATE_DDL_AUTO=none `
        --env 'spring.jpa.hibernate.ddl-auto=none' `
        --env "JAVA_TOOL_OPTIONS=-Xms128m -Xmx$JavaHeap" `
        --mount "type=bind,source=$JarPath,destination=/app/app.jar,readonly" `
        eclipse-temurin:21-jre `
        java -jar /app/app.jar | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Spring container could not be started: $containerName"
    }

    docker network connect dropit-performance_default $containerName
    if ($LASTEXITCODE -ne 0) {
        throw "Spring container could not join the performance network: $containerName"
    }
}

$deadline = (Get-Date).AddSeconds(120)
do {
    $startedCount = 0

    foreach ($containerName in @('dropit-list-app-1', 'dropit-list-app-2')) {
        $state = docker inspect --format '{{.State.Status}}' $containerName
        if ($state -ne 'running') {
            & cmd.exe /d /c "docker logs --tail 80 $containerName 2>&1"
            throw "Spring container stopped during startup: $containerName"
        }

        $logs = & cmd.exe /d /c "docker logs $containerName 2>&1"
        if ($logs -match 'Started DropitServerApplication') {
            $startedCount++
        }
    }

    if ($startedCount -eq 2) {
        break
    }

    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

if ($startedCount -ne 2) {
    throw "Only $startedCount of 2 Spring containers became ready."
}

docker run `
    --detach `
    --name dropit-list-gateway `
    --network dropit-performance_default `
    --publish '127.0.0.1:8080:8080' `
    --mount "type=bind,source=$nginxConfig,destination=/etc/nginx/nginx.conf,readonly" `
    nginx:1.29-alpine | Out-Null

if ($LASTEXITCODE -ne 0) {
    throw 'Nginx gateway could not be started.'
}

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Seconds 2
    $gatewayState = docker inspect --format '{{.State.Status}}' dropit-list-gateway

    if ($gatewayState -ne 'running') {
        & cmd.exe /d /c 'docker logs --tail 80 dropit-list-gateway 2>&1'
        throw "Nginx gateway state: $gatewayState"
    }

    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        Write-Host 'Drop list performance cluster is ready: 2 Spring instances and Nginx.'
        Write-Host 'Host URL: http://localhost:8080'
        exit 0
    }
} while ((Get-Date) -lt $deadline)

throw 'Nginx gateway did not bind port 8080.'
