param(
    [string]$InfluxUrl = 'http://localhost:8086'
)

$ErrorActionPreference = 'Stop'
$rawDirectory = Join-Path $PSScriptRoot 'results/raw'

function Read-Metric($name, $metric, $field) {
    $path = Join-Path $rawDirectory ($name + '.json')
    $summary = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    $values = $summary.metrics.PSObject.Properties[$metric].Value.values
    return [double]$values.PSObject.Properties[$field].Value
}

function Read-Count($name, $metric, $field) {
    return [long](Read-Metric $name $metric $field)
}

$openingRuns = @(
    @{ run = '1'; files = @('portfolio-redis-off-opening-50-sep15', 'portfolio-redis-on-opening-50-sep15', 'portfolio-redis-l1-opening-50-sep15') },
    @{ run = '2'; files = @('portfolio-redis-off-opening-50-repeat-sep15', 'portfolio-redis-on-opening-50-repeat-sep15', 'portfolio-redis-l1-opening-50-repeat-sep15') }
)
$stages = @('DB-direct', 'Redis', 'Redis-L1')
$lines = New-Object System.Collections.Generic.List[string]
$timestamp = [DateTimeOffset]::Parse('2026-09-15T12:00:00Z').ToUnixTimeMilliseconds().ToString() + '000000'

foreach ($entry in $openingRuns) {
    for ($index = 0; $index -lt 3; $index++) {
        $name = $entry.files[$index]
        $p95 = Read-Metric $name 'drop_list_opening_duration' 'p(95)'
        $success = Read-Count $name 'checks' 'passes'
        $stage = $stages[$index]
        $lines.Add(('drop_list_saved_k6,scenario=opening50,run={0},stage={1} p95_ms={2},success={3}i {4}' -f $entry.run, $stage, $p95.ToString('R', [Globalization.CultureInfo]::InvariantCulture), $success, $timestamp))
    }
}

$opening4000Runs = @(
    @{ run = '1'; files = @('opening-4000-redis-sep16', 'opening-4000-l1-sep16') },
    @{ run = '2'; files = @('opening-4000-redis-repeat-sep16', 'opening-4000-l1-repeat-sep16') }
)
$opening4000Timestamp = [DateTimeOffset]::Parse('2026-09-16T03:00:00Z').ToUnixTimeMilliseconds().ToString() + '000000'
foreach ($entry in $opening4000Runs) {
    for ($index = 0; $index -lt 2; $index++) {
        $name = $entry.files[$index]
        $p95 = Read-Metric $name 'drop_list_opening_duration' 'p(95)'
        $success = Read-Count $name 'checks' 'passes'
        $failed = Read-Count $name 'checks' 'fails'
        $stage = $stages[$index + 1]
        $lines.Add(('drop_list_saved_k6,scenario=opening4000,run={0},stage={1} p95_ms={2},success={3}i,failed={4}i {5}' -f $entry.run, $stage, $p95.ToString('R', [Globalization.CultureInfo]::InvariantCulture), $success, $failed, $opening4000Timestamp))
    }
}

$millionFiles = @('portfolio-million-redis-1000rps-sep15', 'portfolio-million-local-1000rps-sep15')
for ($index = 0; $index -lt 2; $index++) {
    $name = $millionFiles[$index]
    $stage = $stages[$index + 1]
    $p95 = Read-Metric $name 'http_req_duration' 'p(95)'
    $success = Read-Count $name 'checks' 'passes'
    $failed = Read-Count $name 'checks' 'fails'
    $dropped = Read-Count $name 'dropped_iterations' 'count'
    $lines.Add(('drop_list_saved_k6,scenario=million,run=1,stage={0} p95_ms={1},success={2}i,failed={3}i,dropped={4}i {5}' -f $stage, $p95.ToString('R', [Globalization.CultureInfo]::InvariantCulture), $success, $failed, $dropped, $timestamp))
}

$body = ($lines -join "`n") + "`n"
Invoke-WebRequest -Method Post -Uri "$InfluxUrl/write?db=k6&precision=ns" -ContentType 'text/plain' -Body $body -UseBasicParsing | Out-Null
Write-Output "Imported $($lines.Count) saved k6 summary points into local InfluxDB (measurement: drop_list_saved_k6)."
