param(
    [string[]]$Symbols = @("INGR"),
    [string[]]$Routes = @("/securities/INGR"),
    [string]$FrontendBaseUrl = "http://localhost:5173",
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$BugFile = "specs/chart-qa-agent/bugs.json",
    [string]$EvidenceRoot = "specs/chart-qa-agent/evidence",
    [string]$AdminEmail = "admin@realdemo.local",
    [string]$AdminPassword = "admin"
)

$ErrorActionPreference = "Stop"

function New-DirectoryForFile([string]$Path) {
    $parent = Split-Path -Parent $Path
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
}

function Invoke-JsonApi {
    param(
        [string]$Method = "GET",
        [string]$Path,
        [hashtable]$Headers = @{},
        [object]$Body = $null
    )
    $uri = if ($Path.StartsWith("http")) { $Path } else { "$BackendBaseUrl$Path" }
    $args = @{ Method = $Method; Uri = $uri; UseBasicParsing = $true; Headers = $Headers }
    if ($null -ne $Body) {
        $args.ContentType = "application/json"
        $args.Body = ($Body | ConvertTo-Json -Depth 12)
    }
    return Invoke-RestMethod @args
}

function Get-AuthHeaders {
    try {
        $auth = Invoke-JsonApi -Method "POST" -Path "/auth/login" -Body @{ email = $AdminEmail; password = $AdminPassword }
        if ($auth.accessToken) { return @{ Authorization = "Bearer $($auth.accessToken)" } }
    } catch {}
    return @{}
}

function New-Bug {
    param(
        [string]$Id,
        [string]$Severity,
        [string]$Route,
        [string]$Surface,
        [string]$Title,
        [string]$Observed,
        [string]$Expected,
        [string]$Reproduction,
        [object]$Evidence,
        [string]$SuggestedOwner = "frontend/backend"
    )
    return [ordered]@{
        id = $Id
        status = "open"
        severity = $Severity
        route = $Route
        surface = $Surface
        title = $Title
        observed = $Observed
        expected = $Expected
        reproduction = $Reproduction
        suggestedOwner = $SuggestedOwner
        detectedAt = (Get-Date).ToString("o")
        lastSeenAt = (Get-Date).ToString("o")
        evidence = $Evidence
    }
}

function Count-DistinctNumericValues {
    param([object[]]$Rows, [string]$Property)
    $values = @($Rows | ForEach-Object { $_.$Property } | Where-Object { $null -ne $_ })
    if ($values.Count -eq 0) { return 0 }
    return @($values | ForEach-Object { [math]::Round([double]$_, 6) } | Select-Object -Unique).Count
}

function Test-AnnualFinancialRangeControlsGuarded {
    $sourcePath = "frontend/src/pages/SecurityDetailPage.tsx"
    if (-not (Test-Path -LiteralPath $sourcePath)) { return $false }

    $source = Get-Content -LiteralPath $sourcePath -Raw
    return $source -match "availableHistoryWindows\(data\.length\)" `
        -and $source -match "windowOptions\.map" `
        -and $source -match "dataLength\s*>=\s*\(historyWindowSize\[option\]"
}

New-DirectoryForFile $BugFile
New-Item -ItemType Directory -Force -Path $EvidenceRoot | Out-Null
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$headers = Get-AuthHeaders
$bugs = @()
$fixedBugEvidence = @{}
$apiEvidence = @()

foreach ($symbol in $Symbols) {
    $route = "/securities/$symbol"
    $symbolEvidence = [ordered]@{ symbol = $symbol; route = $route; probes = @{} }
    try {
        $detail = Invoke-JsonApi -Path "/api/v1/securities/$symbol" -Headers $headers
        $symbolEvidence.probes.detail = [ordered]@{ ok = $true; priceDate = $detail.priceDate; currentPrice = $detail.currentPrice }
        $fixedBugEvidence["CHART-$symbol-DETAIL-UNAVAILABLE"] = $symbolEvidence.probes.detail
    } catch {
        $symbolEvidence.probes.detail = [ordered]@{ ok = $false; error = $_.Exception.Message }
        $bugs += New-Bug -Id "CHART-$symbol-DETAIL-UNAVAILABLE" -Severity "high" -Route $route -Surface "Security detail" `
            -Title "$symbol security detail cannot load" `
            -Observed "The security detail API failed." `
            -Expected "The detail page should load core profile data before rendering chart tabs." `
            -Reproduction "Open $FrontendBaseUrl$route." `
            -Evidence $symbolEvidence.probes.detail
        $apiEvidence += $symbolEvidence
        continue
    }

    foreach ($range in @("1y", "3y", "5y", "10y", "max")) {
        try {
            $prices = Invoke-JsonApi -Path "/api/v1/securities/$symbol/prices?range=$range" -Headers $headers
            $rows = @($prices.prices)
            $symbolEvidence.probes["prices-$range"] = [ordered]@{
                ok = $true
                source = $prices.source
                count = $rows.Count
                first = if ($rows.Count) { $rows[0].date } else { $null }
                last = if ($rows.Count) { $rows[$rows.Count - 1].date } else { $null }
            }
            $minRows = switch ($range) {
                "1y" { 120 }
                "3y" { 360 }
                "5y" { 600 }
                "10y" { 1200 }
                default { 1200 }
            }
            if ($prices.source -ne "FMP" -or $rows.Count -lt $minRows) {
                $bugs += New-Bug -Id "CHART-$symbol-PRICES-$range-DEPTH" -Severity "high" -Route $route -Surface "Quotes tab" `
                    -Title "$symbol quote chart lacks credible $range history" `
                    -Observed "Price history source=$($prices.source), rows=$($rows.Count)." `
                    -Expected "The $range price chart should use FMP historical daily prices with enough points for the selected period." `
                    -Reproduction "Open $FrontendBaseUrl$route, select Quotes, then select $range." `
                    -Evidence $symbolEvidence.probes["prices-$range"]
            }
        } catch {
            $symbolEvidence.probes["prices-$range"] = [ordered]@{ ok = $false; error = $_.Exception.Message }
            $bugs += New-Bug -Id "CHART-$symbol-PRICES-$range-ERROR" -Severity "high" -Route $route -Surface "Quotes tab" `
                -Title "$symbol quote chart request fails for $range" `
                -Observed $_.Exception.Message `
                -Expected "Changing quote range should return a 200 response and render a chart or a clear data gap." `
                -Reproduction "Open $FrontendBaseUrl$route, select Quotes, then select $range." `
                -Evidence $symbolEvidence.probes["prices-$range"]
        }
    }

    try {
        $financials = Invoke-JsonApi -Path "/api/v1/securities/$symbol/financials" -Headers $headers
        $annuals = @($financials.annuals)
        $rangeControlsGuarded = Test-AnnualFinancialRangeControlsGuarded
        $symbolEvidence.probes.financials = [ordered]@{
            ok = $true
            annualCount = $annuals.Count
            years = @($annuals | ForEach-Object { $_.fiscalYear })
            rangeControlsGuarded = $rangeControlsGuarded
        }
        $financialDepthBugId = "CHART-$symbol-FINANCIALS-10Y-DEPTH"
        if ($annuals.Count -lt 10 -and -not $rangeControlsGuarded) {
            $bugs += New-Bug -Id "CHART-$symbol-FINANCIALS-10Y-DEPTH" -Severity "medium" -Route $route -Surface "Financials tab" `
                -Title "$symbol financial charts expose 10y controls without 10 annual points" `
                -Observed "Annual financial rows=$($annuals.Count)." `
                -Expected "10y controls should be disabled/renamed when fewer than 10 distinct annual periods exist." `
                -Reproduction "Open $FrontendBaseUrl$route, select Financials, then inspect chart range controls." `
                -Evidence $symbolEvidence.probes.financials
        } elseif ($annuals.Count -lt 10 -and $rangeControlsGuarded) {
            $fixedBugEvidence[$financialDepthBugId] = $symbolEvidence.probes.financials
        }
    } catch {
        $symbolEvidence.probes.financials = [ordered]@{ ok = $false; error = $_.Exception.Message }
    }

    try {
        $ratios = Invoke-JsonApi -Path "/api/v1/securities/$symbol/ratios" -Headers $headers
        $ratioRows = @($ratios.ratios)
        $peDistinct = Count-DistinctNumericValues -Rows $ratioRows -Property "pe"
        $roicDistinct = Count-DistinctNumericValues -Rows $ratioRows -Property "roic"
        $roeDistinct = Count-DistinctNumericValues -Rows $ratioRows -Property "roe"
        $symbolEvidence.probes.ratios = [ordered]@{
            ok = $true
            rowCount = $ratioRows.Count
            peDistinct = $peDistinct
            roicDistinct = $roicDistinct
            roeDistinct = $roeDistinct
            dates = @($ratioRows | ForEach-Object { $_.date })
        }
        $ratioRepeatedBugId = "CHART-$symbol-RATIOS-REPEATED-SERIES"
        if ($ratioRows.Count -ge 6 -and ($peDistinct + $roicDistinct + $roeDistinct) -le 3) {
            $bugs += New-Bug -Id $ratioRepeatedBugId -Severity "high" -Route $route -Surface "Ratios tab" `
                -Title "$symbol ratio charts show repeated synthetic history" `
                -Observed "Ratios rows=$($ratioRows.Count), distinct PE/ROIC/ROE values=$peDistinct/$roicDistinct/$roeDistinct." `
                -Expected "Historical ratio charts should plot distinct observed periods, or show a data gap instead of repeated copied values." `
                -Reproduction "Open $FrontendBaseUrl$route, select Ratios, switch chart periods." `
                -Evidence $symbolEvidence.probes.ratios
        } else {
            $fixedBugEvidence[$ratioRepeatedBugId] = $symbolEvidence.probes.ratios
        }
    } catch {
        $symbolEvidence.probes.ratios = [ordered]@{ ok = $false; error = $_.Exception.Message }
    }

    try {
        $dividends = Invoke-JsonApi -Path "/api/v1/securities/$symbol/dividends" -Headers $headers
        $dividendRows = @($dividends.history)
        $symbolEvidence.probes.dividends = [ordered]@{ ok = $true; rowCount = $dividendRows.Count; cagr10y = $dividends.cagr10y }
        if ($dividendRows.Count -lt 10) {
            $bugs += New-Bug -Id "CHART-$symbol-DIVIDENDS-DEPTH" -Severity "medium" -Route $route -Surface "Dividends tab" `
                -Title "$symbol dividend chart has insufficient historical depth" `
                -Observed "Dividend rows=$($dividendRows.Count), cagr10y=$($dividends.cagr10y)." `
                -Expected "Dividend charts and 10y CAGR labels should clearly indicate insufficient history." `
                -Reproduction "Open $FrontendBaseUrl$route and select Dividends." `
                -Evidence $symbolEvidence.probes.dividends
        }
    } catch {
        $symbolEvidence.probes.dividends = [ordered]@{ ok = $false; error = $_.Exception.Message }
    }

    $apiEvidence += $symbolEvidence
}

$evidencePath = Join-Path $EvidenceRoot "$runId-api-probes.json"
$apiEvidence | ConvertTo-Json -Depth 12 | Set-Content -Path $evidencePath -Encoding UTF8

$existing = @()
if (Test-Path -LiteralPath $BugFile) {
    $raw = Get-Content -LiteralPath $BugFile -Raw
    if (-not [string]::IsNullOrWhiteSpace($raw)) {
        $parsed = $raw | ConvertFrom-Json
        $existing = if ($parsed.PSObject.Properties.Name -contains "bugs") { @($parsed.bugs) } else { @($parsed) }
    }
}

$byId = [ordered]@{}
foreach ($bug in $existing) { $byId[$bug.id] = $bug }
foreach ($bug in $bugs) {
    if ($byId.Contains($bug.id)) {
        $current = $byId[$bug.id]
        $current.status = if ($current.status -in @("fixed", "in_progress", "needs_agent")) { "reopened" } else { $current.status }
        $current.lastSeenAt = $bug.lastSeenAt
        $current.evidence = $bug.evidence
        $current.observed = $bug.observed
    } else {
        $byId[$bug.id] = $bug
    }
}
foreach ($bugId in $fixedBugEvidence.Keys) {
    $wasRedetected = @($bugs | Where-Object { $_.id -eq $bugId }).Count -gt 0
    if ($byId.Contains($bugId) -and -not $wasRedetected) {
        $current = $byId[$bugId]
        $current.status = "fixed"
        $current.evidence = $fixedBugEvidence[$bugId]
        if ($bugId -like "*-FINANCIALS-10Y-DEPTH") {
            $current.observed = "Annual financial rows=$($fixedBugEvidence[$bugId].annualCount); range controls are guarded."
            $note = "Audit verified guarded annual range controls."
        } elseif ($bugId -like "*-RATIOS-REPEATED-SERIES") {
            $current.observed = "Ratios rows=$($fixedBugEvidence[$bugId].rowCount), distinct PE/ROIC/ROE values=$($fixedBugEvidence[$bugId].peDistinct)/$($fixedBugEvidence[$bugId].roicDistinct)/$($fixedBugEvidence[$bugId].roeDistinct)."
            $note = "Audit did not redetect repeated synthetic ratio history."
        } elseif ($bugId -like "*-DETAIL-UNAVAILABLE") {
            $current.observed = "Security detail loaded; currentPrice=$($fixedBugEvidence[$bugId].currentPrice), priceDate=$($fixedBugEvidence[$bugId].priceDate)."
            $note = "Audit verified that the security detail API loads."
        } else {
            $note = "Audit did not redetect this bug."
        }
        if ($current.PSObject.Properties.Name -contains "lastFixNote") {
            $current.lastFixNote = $note
        } else {
            $current | Add-Member -NotePropertyName "lastFixNote" -NotePropertyValue $note
        }
    }
}

$registry = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    agent = "chart-qa-agent"
    scope = [ordered]@{ symbols = $Symbols; routes = $Routes }
    evidencePath = $evidencePath
    bugs = @($byId.Values)
}
$registry | ConvertTo-Json -Depth 12 | Set-Content -Path $BugFile -Encoding UTF8

$openCount = @($registry.bugs | Where-Object { $_.status -in @("open", "reopened") }).Count
Write-Host "Chart QA agent completed. Bugs open/reopened: $openCount. Registry: $BugFile"
