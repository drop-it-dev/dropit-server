param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Before', 'After')]
    [string]$Phase,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$DropId,

    [ValidateRange(1, 100000)]
    [int]$ConcurrentUsers = 10000,

    [ValidateSet('Warm', 'Cold')]
    [string]$CacheMode = 'Warm',

    [string]$TargetBaseUrl
)

$ErrorActionPreference = 'Stop'

$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDirectory = Split-Path -Parent $performanceDirectory
$environmentFile = Join-Path $projectDirectory '.env.performance.local'
$composeFile = Join-Path $performanceDirectory 'compose.performance.yml'
$k6Directory = Join-Path $performanceDirectory 'k6'
$resultDirectory = Join-Path $performanceDirectory 'results\raw'
$phaseName = $Phase.ToLower()
$testRun = "drop-detail-concurrent-$phaseName-$ConcurrentUsers-vu"
$containerName = "dropit-k6-$testRun"

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw '.env.performance.local is missing.'
}

$settings = @{}
Get-Content -LiteralPath $environmentFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line -split '=', 2

        if ($parts.Count -eq 2) {
            $settings[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
}

$healthCheckUrl = $settings['HEALTH_CHECK_URL']
$baseUrl = $settings['BASE_URL']
$authEmail = $settings['AUTH_EMAIL']
$authPassword = $settings['AUTH_PASSWORD']
$authUsername = $settings['AUTH_USERNAME']

if (-not $healthCheckUrl -or -not $baseUrl -or
    -not $authEmail -or -not $authPassword -or -not $authUsername) {
    throw 'Required performance environment values are missing.'
}

if ($TargetBaseUrl) {
    $baseUrl = $TargetBaseUrl.TrimEnd('/')
}

function Invoke-LoginWithRetry {
    param(
        [string]$Url,
        [string]$Body,
        [int]$MaxAttempts = 5
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            return Invoke-RestMethod `
                -Uri $Url `
                -Method Post `
                -ContentType 'application/json' `
                -Body $Body
        } catch {
            if ($attempt -eq $MaxAttempts) {
                throw
            }

            Start-Sleep -Seconds 2
        }
    }
}

$loginBody = @{
    email = $authEmail
    password = $authPassword
} | ConvertTo-Json

try {
    $tokenResponse = Invoke-LoginWithRetry `
        -Url "$healthCheckUrl/auth/login" `
        -Body $loginBody `
        -MaxAttempts 3
} catch {
    $signupBody = @{
        email = $authEmail
        password = $authPassword
        username = $authUsername
        role = 'USER'
    } | ConvertTo-Json

    try {
        Invoke-RestMethod `
            -Uri "$healthCheckUrl/auth/signup" `
            -Method Post `
            -ContentType 'application/json' `
            -Body $signupBody | Out-Null
    } catch {
        $statusCode = [int]$_.Exception.Response.StatusCode

        if ($statusCode -ne 409) {
            throw
        }
    }

    $tokenResponse = Invoke-LoginWithRetry `
        -Url "$healthCheckUrl/auth/login" `
        -Body $loginBody `
        -MaxAttempts 10
}

$authToken = $tokenResponse.accessToken

if (-not $authToken) {
    throw 'Access token was not returned by the login API.'
}

try {
    $dropResponse = Invoke-RestMethod `
        -Uri "$healthCheckUrl/drops/$DropId" `
        -Headers @{ Authorization = "Bearer $authToken" } `
        -Method Get

    if ([long]$dropResponse.id -ne $DropId) {
        throw 'The returned Drop ID does not match the requested Drop ID.'
    }
} catch {
    throw "Drop $DropId cannot be tested. Cause: $($_.Exception.Message)"
}

if ($CacheMode -eq 'Cold') {
    $redisContainer = docker ps --filter 'name=redis' --format '{{.Names}}' | Select-Object -First 1

    if (-not $redisContainer) {
        throw 'Redis container is not running.'
    }

    docker exec $redisContainer redis-cli DEL "dropit::dropDetail::$DropId" | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw 'The Drop detail cache key could not be removed.'
    }
}

New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

docker compose `
    --file $composeFile `
    --env-file $environmentFile `
    up --detach influxdb grafana | Out-Null

if ($LASTEXITCODE -ne 0) {
    throw 'InfluxDB or Grafana could not be started.'
}

$grafanaReady = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    curl.exe --silent --fail --output NUL 'http://localhost:3000/api/health'

    if ($LASTEXITCODE -eq 0) {
        $grafanaReady = $true
        break
    }

    Start-Sleep -Seconds 1
}

if (-not $grafanaReady) {
    throw 'Grafana did not become ready within 30 seconds.'
}

$existingContainer = docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}'
if ($existingContainer) {
    docker rm --force $containerName | Out-Null
}

$warmCache = if ($CacheMode -eq 'Warm') { 'true' } else { 'false' }
$dockerArguments = @(
    'run', '--rm',
    '--name', $containerName,
    '--network', 'dropit-performance_default',
    '--volume', "${k6Directory}:/scripts:ro",
    '--volume', "${resultDirectory}:/results",
    'grafana/k6:latest',
    'run',
    '--out', 'influxdb=http://influxdb:8086/k6',
    '--env', "BASE_URL=$baseUrl",
    '--env', "TEST_RUN=$testRun",
    '--env', "AUTH_TOKEN=$authToken",
    '--env', "DROP_ID=$DropId",
    '--env', "CONCURRENT_USERS=$ConcurrentUsers",
    '--env', "WARM_CACHE=$warmCache",
    '--tag', "test_run=$testRun",
    '/scripts/drop-detail-concurrent.js'
)

Write-Host "Running $testRun against Drop $DropId."
Write-Host "Concurrent users: $ConcurrentUsers, cache: $CacheMode."
docker @dockerArguments

if ($LASTEXITCODE -ne 0) {
    $resultFile = Join-Path $resultDirectory "$testRun.json"

    if (-not (Test-Path -LiteralPath $resultFile)) {
        throw 'k6 failed before producing a result file.'
    }

    Write-Warning 'The test completed, but one or more performance thresholds were exceeded.'
}

Write-Host "Result: $resultDirectory\$testRun.json"
Write-Host 'Grafana: http://localhost:3000'
