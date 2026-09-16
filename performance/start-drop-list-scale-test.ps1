param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Opening', 'Soak')]
    [string]$Scenario,
    [Parameter(Mandatory = $true)]
    [string]$TestRun,
    [int]$ConcurrentUsers = 10000,
    [int]$RequestRate = 1000,
    [string]$Duration = '17m',
    [int]$PreAllocatedVUs = 200,
    [int]$MaxVUs = 1000,
    [switch]$Influx,
    [switch]$PrepareGeneratedStock,
    [switch]$AllowHighLoad,
    [ValidateSet('Database', 'Redis', 'Local')]
    [string]$CacheStage,
    [string]$JarPath = 'tmp\dropit-server-gradual.jar',
    [switch]$StartCluster
)

$ErrorActionPreference = 'Stop'
$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptDirectory = Join-Path $performanceDirectory 'k6'
$resultDirectory = Join-Path $performanceDirectory 'results\raw'
$stockPreparationScript = Join-Path $performanceDirectory 'prepare-drop-list-stock.ps1'
$clusterScript = Join-Path $performanceDirectory 'start-drop-list-cluster.ps1'
$projectDirectory = Split-Path -Parent $performanceDirectory

if ($TestRun -notmatch '^[a-zA-Z0-9_-]+$') {
    throw 'TestRun must contain only letters, digits, _ or -.'
}

if ($Scenario -eq 'Opening' -and $ConcurrentUsers -lt 1) {
    throw 'ConcurrentUsers must be positive.'
}

if ($Scenario -eq 'Soak' -and ($RequestRate -lt 1 -or $PreAllocatedVUs -lt 1 -or $MaxVUs -lt $PreAllocatedVUs)) {
    throw 'Rate and VU counts must be valid.'
}
if (-not $AllowHighLoad -and (($Scenario -eq 'Opening' -and $ConcurrentUsers -gt 500) -or
        ($Scenario -eq 'Soak' -and ($RequestRate -gt 200 -or $MaxVUs -gt 500)))) {
    throw 'Local safety limit reached. Use -AllowHighLoad only after a smaller test and PC check.'
}

$resultFile = Join-Path $resultDirectory "$TestRun.json"
$metadataFile = Join-Path $resultDirectory "$TestRun.metadata.json"
if ((Test-Path -LiteralPath $resultFile) -or (Test-Path -LiteralPath $metadataFile)) {
    throw "Saved result already exists for $TestRun. Choose another TestRun to preserve evidence."
}
if ($StartCluster) {
    if (-not $CacheStage) { throw 'CacheStage is required when StartCluster is used.' }
    if (-not [System.IO.Path]::IsPathRooted($JarPath)) { $JarPath = Join-Path $projectDirectory $JarPath }
    $redisEnabled = if ($CacheStage -eq 'Database') { 'false' } else { 'true' }
    $localEnabled = if ($CacheStage -eq 'Local') { 'true' } else { 'false' }
    & $clusterScript -JarPath $JarPath -ListRedisCacheEnabled $redisEnabled -ListLocalCacheEnabled $localEnabled
}

& $stockPreparationScript -SeedMissingGeneratedStock:$PrepareGeneratedStock

$scriptName = if ($Scenario -eq 'Opening') { 'drop-list-10k-opening.js' } else { 'drop-list-million-soak.js' }
$containerName = "dropit-k6-$TestRun"
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null

$existing = docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable.' }
if ($existing) { throw "A container named $containerName already exists." }

$arguments = @(
    'run', '--rm', '--name', $containerName,
    '--network', 'dropit-performance_default',
    '--memory', '2g',
    '--volume', "${scriptDirectory}:/scripts:ro",
    '--volume', "${resultDirectory}:/results",
    '--env', 'BASE_URL=http://dropit-list-gateway:8080',
    '--env', "TEST_RUN=$TestRun",
    '--env', "CONCURRENT_USERS=$ConcurrentUsers",
    '--env', "REQUEST_RATE=$RequestRate",
    '--env', "DURATION=$Duration",
    '--env', "PRE_ALLOCATED_VUS=$PreAllocatedVUs",
    '--env', "MAX_VUS=$MaxVUs",
    'grafana/k6:1.7.1', 'run'
)
if ($Influx) { $arguments += @('--out', 'influxdb=http://influxdb:8086/k6') }
$arguments += "/scripts/$scriptName"

Write-Host "Running ${Scenario}: $TestRun."
Write-Host 'The target is /drops?sortType=LATEST&page=0&size=20 via Nginx and two Spring instances.'
docker @arguments
$dockerExit = $LASTEXITCODE
if (-not (Test-Path -LiteralPath $resultFile)) {
    throw "k6 produced no summary (exit $dockerExit). Check whether the local generator ran out of memory."
}

Write-Host "Summary: $resultFile"
$summary = Get-Content -LiteralPath $resultFile -Raw | ConvertFrom-Json
$completed = [int]$summary.metrics.iterations.values.count
$ok = [int]$summary.metrics.checks.values.passes
$failed = [int]$summary.metrics.checks.values.fails
$dropped = if ($null -ne $summary.metrics.dropped_iterations) { [int]$summary.metrics.dropped_iterations.values.count } else { 0 }
$p95 = if ($Scenario -eq 'Opening') { [double]$summary.metrics.drop_list_opening_duration.values.'p(95)' } else { [double]$summary.metrics.http_req_duration.values.'p(95)' }
$metadata = [ordered]@{
    testRun = $TestRun
    scenario = $Scenario
    cacheStage = if ($CacheStage) { $CacheStage } else { 'Unspecified' }
    api = 'GET /drops?sortType=LATEST&page=0&size=20'
    springInstances = 2
    jarSha256 = if ($StartCluster) { (Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash } else { $null }
    requestedConcurrentUsers = if ($Scenario -eq 'Opening') { $ConcurrentUsers } else { $null }
    requestedRps = if ($Scenario -eq 'Soak') { $RequestRate } else { $null }
    duration = if ($Scenario -eq 'Soak') { $Duration } else { $null }
    successfulHttp200 = $ok
    httpFailures = $failed
    dropped = $dropped
    p95Milliseconds = [math]::Round($p95, 2)
    measuredAt = (Get-Date).ToString('o')
}
$metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $metadataFile -Encoding UTF8
Write-Host "Actual iterations: $completed; HTTP 200: $ok; failed checks: $failed; dropped: $dropped."
Write-Host "Cache stage: $($metadata.cacheStage); p95: $($metadata.p95Milliseconds) ms; metadata: $metadataFile"
if ($Scenario -eq 'Soak' -and $ok -lt 1000000) {
    Write-Warning 'The actual successful requests did not reach one million. Do not describe this run as a one-million-view result.'
}
if ($dockerExit -ne 0) {
    Write-Warning "k6 exited with code $dockerExit. Keep the summary but do not call this a successful capacity test."
}
