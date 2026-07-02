param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ArtifactDir = "specs/2026-07-02-l1-prudent-persona-replay-pack/evidence",
    [string]$AdminEmail = "admin@realdemo.local",
    [string]$InvestorEmail = "investor@realdemo.local",
    [string]$Password = "admin",
    [switch]$SkipLiveApi
)

$ErrorActionPreference = "Stop"

$Symbols = @("BRK.B", "JNJ", "PG", "KO", "PEP", "WMT", "MSFT", "ADP", "UNP", "XOM")
$WatchlistSymbols = @(
    [pscustomobject]@{
        Symbol = "PG"
        MonitoringReason = "WAIT_FOR_BETTER_PRICE"
        Rationale = "Defensive consumer staples candidate; monitor for better margin of safety and complete valuation coverage."
    },
    [pscustomobject]@{
        Symbol = "KO"
        MonitoringReason = "WAIT_FOR_BETTER_PRICE"
        Rationale = "High-quality brand profile; keep on watchlist until valuation and data-quality evidence support deeper review."
    },
    [pscustomobject]@{
        Symbol = "JNJ"
        MonitoringReason = "DATA_QUALITY_GAP"
        Rationale = "Defensive health care candidate; track source coverage, freshness, and dividend sustainability before decisions."
    },
    [pscustomobject]@{
        Symbol = "MSFT"
        MonitoringReason = "VALUATION_CONCERN"
        Rationale = "Strong business quality signal; monitor for valuation discipline and concentration impact."
    }
)

function ConvertTo-SafeFileName {
    param([string]$Name)
    return ($Name.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
}

function ConvertTo-RedactedJson {
    param([object]$Value)
    $json = $Value | ConvertTo-Json -Depth 30
    $json = $json -replace '"accessToken"\s*:\s*"[^"]+"', '"accessToken":"<redacted>"'
    $json = $json -replace '"refreshToken"\s*:\s*"[^"]+"', '"refreshToken":"<redacted>"'
    $json = $json -replace '"token"\s*:\s*"[^"]+"', '"token":"<redacted>"'
    return $json
}

function Save-Artifact {
    param(
        [string]$Name,
        [object]$Value
    )
    $path = Join-Path $ArtifactDir $Name
    ConvertTo-RedactedJson $Value | Set-Content -Path $path -Encoding utf8
    return $path
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $args = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $args.Body = ($Body | ConvertTo-Json -Depth 12)
    }

    Invoke-RestMethod @args
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$ArtifactName,
        [scriptblock]$Action
    )

    try {
        $response = & $Action
        $artifact = Save-Artifact -Name $ArtifactName -Value $response
        return [pscustomobject]@{ Step = $Name; Status = "PASS"; Artifact = $artifact; Notes = "" }
    } catch {
        $errorPayload = [pscustomobject]@{
            step = $Name
            error = $_.Exception.Message
        }
        $artifact = Save-Artifact -Name $ArtifactName -Value $errorPayload
        return [pscustomobject]@{ Step = $Name; Status = "FAIL"; Artifact = $artifact; Notes = $_.Exception.Message }
    }
}

function Find-AvailabilityStatus {
    param(
        [object]$Review,
        [string]$Category
    )
    $item = $Review.availability | Where-Object { $_.category -eq $Category } | Select-Object -First 1
    if ($item -and $item.state -and $item.state.status) {
        return $item.state.status
    }
    return "UNKNOWN"
}

function New-ReviewSummary {
    param(
        [string]$Symbol,
        [object]$Review
    )

    $freshness = @()
    if ($Review.freshness) {
        $freshness = $Review.freshness | ForEach-Object {
            [pscustomobject]@{
                category = $_.category
                status = $_.status
                dataAsOf = $_.dataAsOf
                message = $_.message
            }
        }
    }

    $sourceCoverage = @()
    if ($Review.sourceCoverage) {
        $sourceCoverage = $Review.sourceCoverage | ForEach-Object {
            [pscustomobject]@{
                category = $_.category
                provider = $_.provider
                status = $_.status
                message = $_.message
            }
        }
    }

    return [pscustomobject]@{
        symbol = $Symbol
        scoreStatus = Find-AvailabilityStatus -Review $Review -Category "Score"
        valuationStatus = Find-AvailabilityStatus -Review $Review -Category "Valuation"
        quoteStatus = Find-AvailabilityStatus -Review $Review -Category "Quote"
        marginOfSafety = $Review.valuation.marginOfSafety
        recommendation = $Review.valuation.recommendation
        sourceCoverage = $sourceCoverage
        freshness = $freshness
        dataQualityNotes = $Review.dataQualityNotes
        reviewCaptured = $null -ne $Review.symbol
    }
}

function New-HoldingRequest {
    param(
        [string]$Symbol,
        [object]$Review,
        [decimal]$TargetAmount = 1000
    )

    $price = $null
    if ($Review.detail -and $Review.detail.currentPrice) {
        $price = [decimal]$Review.detail.currentPrice
    }
    if ($price -and $price -gt 0) {
        $quantity = [Math]::Max(1, [Math]::Round($TargetAmount / $price, 0))
        return @{ symbol = $Symbol; quantity = $quantity; averageCostBasis = $price; currency = "USD" }
    }
    return @{ symbol = $Symbol; quantity = 1; averageCostBasis = 100; currency = "USD" }
}

New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null

if ($SkipLiveApi) {
    $manifest = [pscustomobject]@{
        mode = "dry-run"
        baseUrl = $BaseUrl
        symbols = $Symbols
        watchlistSymbols = $WatchlistSymbols
        plannedEvidence = @(
            "seed-results.json",
            "review-summary.json",
            "equal-weight-portfolio.json",
            "equal-weight-portfolio-detail.json",
            "oversized-concentration-portfolio.json",
            "oversized-concentration-detail.json",
            "watchlist-reload.json",
            "replay-summary.md"
        )
        note = "Dry run only. Start the local backend and rerun without -SkipLiveApi for live evidence. This replay is decision-support workflow evidence, not investment advice."
    }
    Save-Artifact -Name "dry-run-manifest.json" -Value $manifest | Out-Null
    Write-Host "L1 dry-run manifest written to $ArtifactDir"
    exit 0
}

$results = @()
$tokens = @{}

$results += Invoke-Step -Name "Admin login" -ArtifactName "admin-login.json" -Action {
    $login = Invoke-Json -Method "POST" -Path "/auth/login" -Body @{ email = $AdminEmail; password = $Password }
    if (-not $login.accessToken) { throw "Admin login did not return accessToken." }
    $tokens["admin"] = $login.accessToken
    $login
}

$results += Invoke-Step -Name "Investor login" -ArtifactName "investor-login.json" -Action {
    $login = Invoke-Json -Method "POST" -Path "/auth/login" -Body @{ email = $InvestorEmail; password = $Password }
    if (-not $login.accessToken) { throw "Investor login did not return accessToken." }
    $tokens["investor"] = $login.accessToken
    $login
}

$adminToken = $tokens["admin"]
$investorToken = $tokens["investor"]

$encodedTickers = [uri]::EscapeDataString(($Symbols -join ","))
$results += Invoke-Step -Name "Seed prudent symbol set" -ArtifactName "seed-results.json" -Action {
    Invoke-Json -Method "POST" -Path "/api/v1/admin/seed?tickers=$encodedTickers" -Token $adminToken
}

$script:ReviewPayloads = @{}
$script:ReviewRows = @()
foreach ($symbol in $Symbols) {
    $safeSymbol = ConvertTo-SafeFileName $symbol
    $results += Invoke-Step -Name "$symbol review packet" -ArtifactName "review-$safeSymbol.json" -Action {
        $review = Invoke-Json -Method "GET" -Path "/api/v1/securities/$symbol/review" -Token $investorToken
        $script:ReviewPayloads[$symbol] = $review
        $script:ReviewRows += New-ReviewSummary -Symbol $symbol -Review $review
        $review
    }
}

$reviewSummary = [pscustomobject]@{
    symbols = $Symbols
    rows = $script:ReviewRows
    decisionSupportBoundary = "This replay records conservative research workflow evidence only. It is not investment advice or an investable model portfolio."
}
Save-Artifact -Name "review-summary.json" -Value $reviewSummary | Out-Null

$script:EqualPortfolioId = $null
$results += Invoke-Step -Name "Create equal-weight validation portfolio" -ArtifactName "equal-weight-portfolio.json" -Action {
    $portfolio = Invoke-Json -Method "POST" -Path "/api/v1/portfolios" -Token $investorToken -Body @{
        name = "L1 prudent validation equal weight"
        description = "Replay evidence portfolio for concentration checks; not an investable model."
    }
    $script:EqualPortfolioId = $portfolio.id
    $portfolio
}

if ($script:EqualPortfolioId) {
    foreach ($symbol in $Symbols) {
        $safeSymbol = ConvertTo-SafeFileName $symbol
        $review = $script:ReviewPayloads[$symbol]
        $holding = New-HoldingRequest -Symbol $symbol -Review $review -TargetAmount 1000
        $results += Invoke-Step -Name "$symbol equal-weight holding" -ArtifactName "holding-equal-$safeSymbol.json" -Action {
            Invoke-Json -Method "POST" -Path "/api/v1/portfolios/$script:EqualPortfolioId/holdings" -Token $investorToken -Body $holding
        }
    }

    $results += Invoke-Step -Name "Equal-weight concentration detail" -ArtifactName "equal-weight-portfolio-detail.json" -Action {
        $detail = Invoke-Json -Method "GET" -Path "/api/v1/portfolios/$script:EqualPortfolioId" -Token $investorToken
        $holdingBreaches = @($detail.concentrationWarnings | Where-Object { $_.type -eq "HOLDING" })
        [pscustomobject]@{
            portfolio = $detail
            holdingConcentrationBreaches = $holdingBreaches.Count
            passesExpectedCheck = ($holdingBreaches.Count -eq 0)
            note = "Expected: no single holding breaches the configured holding concentration threshold when prices are available."
        }
    }
}

$script:OversizedPortfolioId = $null
$results += Invoke-Step -Name "Create oversized KO concentration portfolio" -ArtifactName "oversized-concentration-portfolio.json" -Action {
    $portfolio = Invoke-Json -Method "POST" -Path "/api/v1/portfolios" -Token $investorToken -Body @{
        name = "L1 oversized KO concentration"
        description = "Replay evidence portfolio for holding concentration warnings; not an investable model."
    }
    $script:OversizedPortfolioId = $portfolio.id
    $portfolio
}

if ($script:OversizedPortfolioId) {
    $oversizedHoldings = @(
        @{ symbol = "KO"; quantity = 100; averageCostBasis = 60; currency = "USD" },
        @{ symbol = "JNJ"; quantity = 5; averageCostBasis = 150; currency = "USD" },
        @{ symbol = "PG"; quantity = 5; averageCostBasis = 160; currency = "USD" }
    )
    foreach ($holding in $oversizedHoldings) {
        $safeSymbol = ConvertTo-SafeFileName $holding.symbol
        $results += Invoke-Step -Name "$($holding.symbol) oversized holding" -ArtifactName "holding-oversized-$safeSymbol.json" -Action {
            Invoke-Json -Method "POST" -Path "/api/v1/portfolios/$script:OversizedPortfolioId/holdings" -Token $investorToken -Body $holding
        }
    }

    $results += Invoke-Step -Name "Oversized concentration detail" -ArtifactName "oversized-concentration-detail.json" -Action {
        $detail = Invoke-Json -Method "GET" -Path "/api/v1/portfolios/$script:OversizedPortfolioId" -Token $investorToken
        $holdingBreaches = @($detail.concentrationWarnings | Where-Object { $_.type -eq "HOLDING" })
        [pscustomobject]@{
            portfolio = $detail
            holdingConcentrationBreaches = $holdingBreaches.Count
            passesExpectedCheck = ($holdingBreaches.Count -gt 0)
            note = "Expected: oversized KO scenario produces at least one holding concentration warning when prices are available."
        }
    }
}

foreach ($item in $WatchlistSymbols) {
    $safeSymbol = ConvertTo-SafeFileName $item.Symbol
    $results += Invoke-Step -Name "$($item.Symbol) watchlist rationale" -ArtifactName "watchlist-add-$safeSymbol.json" -Action {
        Invoke-Json -Method "POST" -Path "/api/v1/watchlist" -Token $investorToken -Body @{
            symbol = $item.Symbol
            mosAlertMin = 15
            mosAlertMax = $null
            fundamentalDegradeThreshold = 50
            monitoringReason = $item.MonitoringReason
            rationaleNote = $item.Rationale
        }
    }
}

$results += Invoke-Step -Name "Watchlist rationale reload" -ArtifactName "watchlist-reload.json" -Action {
    $watchlist = Invoke-Json -Method "GET" -Path "/api/v1/watchlist" -Token $investorToken
    $coverage = foreach ($item in $WatchlistSymbols) {
        $match = $watchlist | Where-Object { $_.symbol -eq $item.Symbol } | Select-Object -First 1
        [pscustomobject]@{
            symbol = $item.Symbol
            found = $null -ne $match
            rationalePersisted = $match -and $match.rationaleNote -eq $item.Rationale
            monitoringReason = $match.monitoringReason
        }
    }
    [pscustomobject]@{
        watchlist = $watchlist
        requiredRationaleCoverage = $coverage
        passesExpectedCheck = -not ($coverage | Where-Object { -not $_.found -or -not $_.rationalePersisted })
    }
}

$summaryPath = Join-Path $ArtifactDir "replay-summary.md"
$lines = @(
    "# L1 Prudent Persona Replay Summary",
    "",
    "Base URL: $BaseUrl",
    "Symbols: $($Symbols -join ', ')",
    "",
    "| Step | Status | Artifact | Notes |",
    "|---|---|---|---|"
)

foreach ($result in $results) {
    $safeNotes = ($result.Notes -replace "\|", "/")
    $lines += "| $($result.Step) | $($result.Status) | `$($result.Artifact)` | $safeNotes |"
}

$lines += ""
$lines += "Decision-support boundary: this replay records conservative research workflow evidence only. It is not investment advice or an investable model portfolio."
$lines | Set-Content -Path $summaryPath -Encoding utf8

$results | Format-Table -AutoSize
Write-Host ""
Write-Host "L1 replay summary written to $summaryPath"
