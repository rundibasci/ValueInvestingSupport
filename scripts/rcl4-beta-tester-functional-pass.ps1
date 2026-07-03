param(
    [string]$CycleId = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$OutputRoot = "specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence",
    [string]$PortfolioCsv = "C:\Users\Marcello\Downloads\Portfolio.csv",
    [string]$FrontendBaseUrl = "http://localhost:5173",
    [string]$BackendBaseUrl = "http://localhost:8080",
    [switch]$SkipLiveApi
)

$ErrorActionPreference = "Stop"

$cycleDir = Join-Path $OutputRoot $CycleId
New-Item -ItemType Directory -Force -Path $cycleDir | Out-Null

$timestamp = (Get-Date).ToString("o")
$personas = @(
    [ordered]@{ id = "investor"; focus = "Watchlist, screener, portfolio, KO/MSFT review packets, decision-support clarity." },
    [ordered]@{ id = "advisor-compliance"; focus = "Restricted/admin flows, acknowledgement copy, recommendation wording, audit rationale visibility." },
    [ordered]@{ id = "ui-accessibility"; focus = "One main landmark, responsive overflow, disabled states, chart container warnings." },
    [ordered]@{ id = "data-quality-api"; focus = "Screener validation, dividend/growth/insider unavailable states, backend 5xx scan." },
    [ordered]@{ id = "real-portfolio-csv"; focus = "Portfolio.csv mapping, symbols, quantities, duplicate holdings, missing values, Berkshire normalization." }
)

$csvExists = Test-Path -LiteralPath $PortfolioCsv
$csvInspection = [ordered]@{
    path = $PortfolioCsv
    exists = $csvExists
    rowCount = 0
    headers = @()
    probableSymbolColumn = $null
    probableQuantityColumn = $null
    probableValueColumn = $null
    duplicateSymbols = @()
    blankSymbols = 0
    berkshireAliases = @()
    notes = @()
}

if ($csvExists) {
    $rows = Import-Csv -LiteralPath $PortfolioCsv
    $csvInspection.rowCount = @($rows).Count
    if ($rows.Count -gt 0) {
        $headers = $rows[0].PSObject.Properties.Name
        $csvInspection.headers = @($headers)
        $symbolHeader = $headers | Where-Object { $_ -match '^(symbol|ticker|isin|security|strumento|titolo|codice|code)$|symbol|ticker|isin' } | Select-Object -First 1
        $quantityHeader = $headers | Where-Object { $_ -match 'quantity|qty|shares|pezzi|quantit' } | Select-Object -First 1
        $valueHeader = $headers | Where-Object { $_ -match 'value|market|amount|controvalore|valore|peso|weight' } | Select-Object -First 1
        $csvInspection.probableSymbolColumn = $symbolHeader
        $csvInspection.probableQuantityColumn = $quantityHeader
        $csvInspection.probableValueColumn = $valueHeader
        if ($symbolHeader) {
            $symbols = @($rows | ForEach-Object { "$($_.$symbolHeader)".Trim().ToUpperInvariant() })
            $csvInspection.blankSymbols = @($symbols | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count
            $csvInspection.duplicateSymbols = @($symbols | Where-Object { $_ } | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
            $csvInspection.berkshireAliases = @($symbols | Where-Object { $_ -in @("BRK.B", "BRK-B") } | Select-Object -Unique)
            if ($csvInspection.berkshireAliases.Count -gt 0) {
                $csvInspection.notes += "Berkshire Class B aliases detected; application canonical form is BRK-B."
            }
        } else {
            $csvInspection.notes += "No obvious symbol/ticker column detected; manual mapping required."
        }
    } else {
        $csvInspection.notes += "CSV file is present but has no rows."
    }
} else {
    $csvInspection.notes += "CSV file was not found at the configured path; live real-portfolio tester is blocked until the file is available."
}

$liveProbes = @()
if (-not $SkipLiveApi) {
    foreach ($uri in @("$FrontendBaseUrl/watchlist", "$FrontendBaseUrl/securities/KO/review", "$BackendBaseUrl/actuator/health")) {
        try {
            $response = Invoke-WebRequest -Method Get -Uri $uri -UseBasicParsing
            $liveProbes += [ordered]@{ uri = $uri; status = [int]$response.StatusCode; ok = $true }
        } catch {
            $statusCode = $null
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            $liveProbes += [ordered]@{ uri = $uri; status = $statusCode; ok = $false; error = $_.Exception.Message }
        }
    }
}

$manifest = [ordered]@{
    cycleId = $CycleId
    generatedAt = $timestamp
    mode = if ($SkipLiveApi) { "dry-run" } else { "live-probe" }
    portfolioCsv = $PortfolioCsv
    personas = $personas
    liveProbes = $liveProbes
    gate = [ordered]@{
        requiresTwoConsecutiveCleanCycles = $true
        blocksOnHighOrMediumSeverity = $true
        blocksOnRawAdminForbidden = $true
        blocksOnUnexpected5xx = $true
        blocksOnDataQualityContradiction = $true
    }
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $cycleDir "manifest.json") -Encoding UTF8
$csvInspection | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $cycleDir "portfolio-csv-inspection.json") -Encoding UTF8

@"
# Beta Tester Matrix - $CycleId

Generated: $timestamp

| Persona | Focus |
|---|---|
$($personas | ForEach-Object { "| $($_.id) | $($_.focus) |" } | Out-String)

## Cycle Instructions

- Capture route/API, role, action, expected result, actual result, screenshot/log evidence, and severity.
- Classify each finding as investor workflow, advisor/compliance, UI/accessibility, data-quality/API, or real-portfolio CSV.
- Do not describe any portfolio, shortlist, or watchlist as personalized advice.
"@ | Set-Content -Path (Join-Path $cycleDir "beta-tester-matrix.md") -Encoding UTF8

@"
# Beta Gate Report - $CycleId

## Current Gate Result

Dry-run artifact generation passed. Live beta gate remains pending until the app stack is exercised by all personas.

## CSV Tester Status

- CSV path: `$PortfolioCsv`
- Exists: $csvExists
- Rows: $($csvInspection.rowCount)
- Probable symbol column: $($csvInspection.probableSymbolColumn)
- Probable quantity column: $($csvInspection.probableQuantityColumn)
- Probable value/weight column: $($csvInspection.probableValueColumn)
- Duplicate symbols: $($csvInspection.duplicateSymbols -join ', ')
- Blank symbols: $($csvInspection.blankSymbols)
- Notes: $($csvInspection.notes -join ' ')

## Pass Criteria Before K1

- Two consecutive live beta cycles report no new high- or medium-severity findings.
- Any remaining low-severity finding is accepted or deferred with owner, rationale, and target phase.
- CSV portfolio mapping is either successful or blocked with clear unsupported-column reporting.
"@ | Set-Content -Path (Join-Path $cycleDir "beta-gate-report.md") -Encoding UTF8

@"
# Decision-Support Boundary - $CycleId

This beta cycle validates product behavior, data mapping, availability states, compliance-sensitive copy, and workflow clarity. It does not provide personalized investment advice or trade recommendations.
"@ | Set-Content -Path (Join-Path $cycleDir "decision-support-boundary.md") -Encoding UTF8

Write-Host "RCL4 beta tester artifacts written to $cycleDir"
