param(
    [switch]$SeedMissingGeneratedStock
)

$ErrorActionPreference = 'Stop'
$page = Invoke-RestMethod -Uri 'http://localhost:8080/drops?sortType=LATEST&page=0&size=20' -TimeoutSec 90

if ($page.content.Count -ne 20) {
    throw "Expected 20 Drops in the first page, found $($page.content.Count)."
}

$missing = @()
foreach ($drop in $page.content) {
    $key = "{drop:$($drop.id)}:stock"
    $exists = docker exec dropit-redis-1 redis-cli EXISTS $key
    if ($LASTEXITCODE -ne 0) { throw "Redis EXISTS failed for Drop $($drop.id)." }
    if ($exists -eq '0') { $missing += $drop }
}

if ($missing.Count -gt 0 -and -not $SeedMissingGeneratedStock) {
    throw "First-page Redis stock is missing for $($missing.Count) of 20 Drops. Use -SeedMissingGeneratedStock only for generated local performance Drops."
}

if ($SeedMissingGeneratedStock) {
    foreach ($drop in $missing) {
        if ($drop.productName -notlike 'Drop Index Performance Product *') {
            throw "Drop $($drop.id) is not generated performance data. Stock was not changed."
        }
    }
    foreach ($drop in $missing) {
        $key = "{drop:$($drop.id)}:stock"
        $created = docker exec dropit-redis-1 redis-cli SETNX $key $drop.remainingQuantity
        if ($LASTEXITCODE -ne 0) { throw "Redis SETNX failed for Drop $($drop.id)." }
        if ($created -ne '1' -and $created -ne '0') {
            throw "Unexpected Redis SETNX result for Drop $($drop.id)."
        }
    }
}

$present = 0
foreach ($drop in $page.content) {
    $key = "{drop:$($drop.id)}:stock"
    $exists = docker exec dropit-redis-1 redis-cli EXISTS $key
    if ($LASTEXITCODE -ne 0) { throw "Redis verification failed for Drop $($drop.id)." }
    if ($exists -eq '1') { $present++ }
}

if ($present -ne 20) { throw "Only $present of 20 first-page stock keys are ready." }
Write-Host "First-page Redis stock ready: $present/20; newly seeded: $($missing.Count)."
