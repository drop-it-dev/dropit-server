param(
    [string]$InfluxUrl = 'http://localhost:8086',
    [string]$Database = 'k6'
)

$ErrorActionPreference = 'Stop'

$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$rawDirectory = Join-Path $performanceDirectory 'results\raw'
$stages = @('before', 'after', 'optimized')
$points = @()
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000

foreach ($stage in $stages) {
    $resultPath = Join-Path $rawDirectory "drop-detail-concurrent-$stage-10000-vu.json"

    if (-not (Test-Path -LiteralPath $resultPath)) {
        throw "k6 summary is missing: $resultPath"
    }

    $summary = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    $metrics = $summary.metrics
    $duration = $metrics.'http_req_duration{endpoint:drop-detail}'.values
    $failure = $metrics.'http_req_failed{endpoint:drop-detail}'.values.rate
    $success = $metrics.checks.values.rate
    $requests = $metrics.iterations.values.count
    $durationSeconds = $summary.state.testRunDurationMs / 1000

    $fields = @(
        "concurrent_users=10000i",
        "requests=${requests}i",
        "avg_ms=$($duration.avg)",
        "p95_ms=$($duration.'p(95)')",
        "max_ms=$($duration.max)",
        "failure_rate=$failure",
        "success_rate=$success",
        "duration_seconds=$durationSeconds"
    ) -join ','

    $points += "drop_detail_concurrent_summary,stage=$stage $fields $timestamp"
    $timestamp++
}

$dropQuery = [Uri]::EscapeDataString('DROP MEASUREMENT drop_detail_concurrent_summary')
Invoke-RestMethod `
    -Uri "$InfluxUrl/query?db=$Database&q=$dropQuery" `
    -Method Post | Out-Null

Invoke-RestMethod `
    -Uri "$InfluxUrl/write?db=$Database&precision=ns" `
    -Method Post `
    -ContentType 'text/plain' `
    -Body ($points -join "`n") | Out-Null

Write-Host 'Published exact k6 JSON summaries for Before, After, and Optimized.'
