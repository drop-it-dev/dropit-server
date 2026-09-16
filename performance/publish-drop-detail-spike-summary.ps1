param(
    [string]$InfluxUrl = 'http://localhost:8086',
    [string]$Database = 'k6'
)

$ErrorActionPreference = 'Stop'

$performanceDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$rawDirectory = Join-Path $performanceDirectory 'results\raw'
$stages = @('before', 'query', 'after', 'livestock')
$points = @()
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000

foreach ($stage in $stages) {
    $resultPath = Join-Path $rawDirectory "drop-detail-spike-$stage.json"

    if (-not (Test-Path -LiteralPath $resultPath)) {
        throw "k6 summary is missing: $resultPath"
    }

    $summary = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    $metrics = $summary.metrics
    $duration = $metrics.http_req_duration.values
    $requests = $metrics.http_reqs.values
    $failure = $metrics.http_req_failed.values.rate
    $dropped = $metrics.dropped_iterations.values.count

    $fields = @(
        "requests=$($requests.count)i",
        "rps=$($requests.rate)",
        "avg_ms=$($duration.avg)",
        "p95_ms=$($duration.'p(95)')",
        "max_ms=$($duration.max)",
        "failure_rate=$failure",
        "dropped_iterations=$($dropped)i"
    ) -join ','

    $points += "drop_detail_spike_summary,stage=$stage $fields $timestamp"
    $timestamp++
}

$dropQuery = [Uri]::EscapeDataString('DROP MEASUREMENT drop_detail_spike_summary')
Invoke-RestMethod `
    -Uri "$InfluxUrl/query?db=$Database&q=$dropQuery" `
    -Method Post | Out-Null

Invoke-RestMethod `
    -Uri "$InfluxUrl/write?db=$Database&precision=ns" `
    -Method Post `
    -ContentType 'text/plain' `
    -Body ($points -join "`n") | Out-Null

Write-Host 'Published exact k6 JSON summaries for Before, Query, After, and LiveStock.'
