param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Before', 'After', 'Optimized')]
    [string]$Phase
)

$ErrorActionPreference = 'Stop'

$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDirectory = Split-Path -Parent $performanceDirectory
$environmentFile = Join-Path $projectDirectory '.env.performance.local'
$composeFile = Join-Path $performanceDirectory 'compose.performance.yml'
$scriptDirectory = Join-Path $performanceDirectory 'k6'
$rawResultDirectory = Join-Path $performanceDirectory 'results\raw'
$containerName = "dropit-k6-drop-index-$($Phase.ToLower())"
$testRun = "drop-million-$($Phase.ToLower())"
$grafanaUrl = 'http://localhost:3000/d/drop-million-comparison/drop-million-before-index-optimized?orgId=1&from=now-24h&to=now'

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
    throw 'HEALTH_CHECK_URL, BASE_URL, AUTH_EMAIL, AUTH_PASSWORD, and AUTH_USERNAME are required.'
}

$dropListUrl = "$healthCheckUrl/drops?status=OPEN&sortType=CLOSING_SOON&page=0&size=20"
$loginBody = @{
    email = $authEmail
    password = $authPassword
} | ConvertTo-Json

try {
    $tokenResponse = Invoke-RestMethod `
        -Uri "$healthCheckUrl/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $loginBody
} catch {
    $signupBody = @{
        email = $authEmail
        password = $authPassword
        username = $authUsername
        role = 'USER'
    } | ConvertTo-Json

    Invoke-RestMethod `
        -Uri "$healthCheckUrl/auth/signup" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $signupBody | Out-Null

    $tokenResponse = Invoke-RestMethod `
        -Uri "$healthCheckUrl/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $loginBody
}

$authToken = $tokenResponse.accessToken

if (-not $authToken) {
    throw 'Access token was not returned by the login API.'
}

try {
    $response = Invoke-WebRequest `
        -Uri $dropListUrl `
        -Headers @{ Authorization = "Bearer $authToken" } `
        -TimeoutSec 10 `
        -UseBasicParsing
    if ($response.StatusCode -ne 200) {
        throw "Drop API returned HTTP $($response.StatusCode)."
    }
} catch {
    throw "Cannot reach the Spring server or Drop API. Cause: $($_.Exception.Message)"
}

New-Item -ItemType Directory -Path $rawResultDirectory -Force | Out-Null

docker compose `
    --file $composeFile `
    --env-file $environmentFile `
    up --detach influxdb grafana | Out-Null

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

$dockerArguments = @(
    'run', '--rm',
    '--name', $containerName,
    '--network', 'dropit-performance_default',
    '--volume', "${scriptDirectory}:/scripts:ro",
    '--volume', "${rawResultDirectory}:/results",
    '--env', "BASE_URL=$baseUrl",
    '--env', "TEST_RUN=$testRun",
    '--env', "AUTH_TOKEN=$authToken",
    'grafana/k6:latest',
    'run',
    '--out', 'influxdb=http://influxdb:8086/k6',
    '--tag', "test_run=$testRun",
    '/scripts/drop-list-index.js'
)

Write-Host "Running $testRun with 10 to 200 requests per second."
docker @dockerArguments

if ($LASTEXITCODE -ne 0) {
    $resultFile = Join-Path $rawResultDirectory "$testRun.json"
    if (-not (Test-Path -LiteralPath $resultFile)) {
        throw "k6 $Phase test failed before producing a result file."
    }
    Write-Warning "k6 thresholds were exceeded. The measured result was still saved."
}

Write-Host "Raw result: $rawResultDirectory\$testRun.json"
Write-Host "Dashboard: $grafanaUrl"
