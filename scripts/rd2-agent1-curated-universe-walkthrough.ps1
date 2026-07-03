param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ArtifactDir = "specs/2026-07-03-rd2-curated-demo/evidence",
    [string]$AdminEmail = "admin@realdemo.local",
    [string]$InvestorEmail = "investor@realdemo.local",
    [string]$Password = "admin",
    [switch]$SkipLiveApi
)

$ErrorActionPreference = "Stop"

$CuratedSymbols = @(
    "AAPL", "MSFT", "KO", "JNJ", "PG", "PEP", "WMT", "BRK-B", "UNP", "XOM",
    "ADP", "COST", "CL", "MCD", "LOW", "HD", "NKE", "MDT", "TGT", "TXN"
)

$TopCandidateSymbols = @("KO", "JNJ", "PG", "PEP", "WMT")

$DefensiveQualityCriteria = @{
    exchanges = @("NYSE", "NASDAQ")
    countries = @("US")
    sectors = @("Consumer Defensive", "Healthcare", "Industrials", "Technology")
    excludeSectors = $false
    marketCapMin = 10000000000
    marketCapMax = $null
    volumeMin = 500000
    maxSymbols = 30
    sortBy = "MARKET_CAP_DESC"
}

$Steps = @(
    [pscustomobject]@{ Name = "Admin login"; Method = "POST"; Path = "/auth/login"; Role = "admin"; Body = @{ email = $AdminEmail; password = $Password } },
    [pscustomobject]@{ Name = "Investor login"; Method = "POST"; Path = "/auth/login"; Role = "investor"; Body = @{ email = $InvestorEmail; password = $Password } },
    [pscustomobject]@{ Name = "Backend health"; Method = "GET"; Path = "/actuator/health"; Role = "public"; Body = $null },
    [pscustomobject]@{ Name = "Universe templates"; Method = "GET"; Path = "/api/v1/admin/universe/templates"; Role = "admin"; Body = $null },
    [pscustomobject]@{ Name = "Defensive quality preview"; Method = "POST"; Path = "/api/v1/admin/universe/preview"; Role = "admin"; Body = $DefensiveQualityCriteria },
    [pscustomobject]@{ Name = "Seed curated universe"; Method = "POST"; Path = "/api/v1/admin/universe/seed"; Role = "admin"; Body = $DefensiveQualityCriteria },
    [pscustomobject]@{ Name = "Job definitions"; Method = "GET"; Path = "/api/v1/admin/jobs"; Role = "admin"; Body = $null },
    [pscustomobject]@{ Name = "Job run history"; Method = "GET"; Path = "/api/v1/admin/jobs/runs"; Role = "admin"; Body = $null },
    [pscustomobject]@{ Name = "Curated screener"; Method = "POST"; Path = "/api/v1/screener"; Role = "investor"; Body = @{ page = 0; pageSize = 10; preset = "conservative-research"; sortBy = "valueScore"; sortDirection = "DESC"; symbols = $CuratedSymbols } },
    [pscustomobject]@{ Name = "KO review packet"; Method = "GET"; Path = "/api/v1/securities/KO/review"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "JNJ review packet"; Method = "GET"; Path = "/api/v1/securities/JNJ/review"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Top five comparison"; Method = "GET"; Path = "/api/v1/conservative-workflow/agent-one-comparison"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Portfolio list"; Method = "GET"; Path = "/api/v1/portfolios"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Watchlist"; Method = "GET"; Path = "/api/v1/watchlist"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Dashboard summary"; Method = "GET"; Path = "/api/v1/dashboard"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Account"; Method = "GET"; Path = "/api/v1/account"; Role = "investor"; Body = $null }
)

function ConvertTo-SafeFileName {
    param([string]$Name)
    return ($Name.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
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

function ConvertTo-RedactedJson {
    param([object]$Value)

    $json = $Value | ConvertTo-Json -Depth 24
    $json = $json -replace '"accessToken"\s*:\s*"[^"]+"', '"accessToken":"<redacted>"'
    $json = $json -replace '"refreshToken"\s*:\s*"[^"]+"', '"refreshToken":"<redacted>"'
    $json = $json -replace '"token"\s*:\s*"[^"]+"', '"token":"<redacted>"'
    return $json
}

New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null

if ($SkipLiveApi) {
    $manifest = [pscustomobject]@{
        mode = "dry-run"
        baseUrl = $BaseUrl
        template = "defensive-quality"
        curatedSymbols = $CuratedSymbols
        topCandidateSymbols = $TopCandidateSymbols
        steps = $Steps | ForEach-Object {
            [pscustomobject]@{
                name = $_.Name
                method = $_.Method
                path = $_.Path
                role = $_.Role
            }
        }
        screenshotChecklist = "specs/2026-07-03-rd2-curated-demo/screenshots/README.md"
        note = "Dry run only. Start docker compose -f docker-compose.realDemo.yml up --build and rerun without -SkipLiveApi for live curated-universe evidence."
        boundary = "Workflow validation only; not investment advice."
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $ArtifactDir "dry-run-manifest.json") -Encoding utf8
    Write-Host "RD2-1 dry-run manifest written to $ArtifactDir"
    exit 0
}

$tokens = @{}
$results = @()

foreach ($step in $Steps) {
    $fileName = "{0}.json" -f (ConvertTo-SafeFileName $step.Name)
    $path = Join-Path $ArtifactDir $fileName

    try {
        $token = $null
        if ($step.Role -eq "admin" -and $tokens.ContainsKey("admin")) { $token = $tokens["admin"] }
        if ($step.Role -eq "investor" -and $tokens.ContainsKey("investor")) { $token = $tokens["investor"] }

        $response = Invoke-Json -Method $step.Method -Path $step.Path -Body $step.Body -Token $token

        if ($step.Name -eq "Admin login" -and $response.accessToken) { $tokens["admin"] = $response.accessToken }
        if ($step.Name -eq "Investor login" -and $response.accessToken) { $tokens["investor"] = $response.accessToken }

        ConvertTo-RedactedJson -Value $response | Set-Content -Path $path -Encoding utf8
        $results += [pscustomobject]@{ Step = $step.Name; Status = "PASS"; Artifact = $path; Notes = "" }
    } catch {
        $message = $_.Exception.Message
        [pscustomobject]@{ error = $message; step = $step.Name; method = $step.Method; path = $step.Path } |
            ConvertTo-Json -Depth 8 |
            Set-Content -Path $path -Encoding utf8
        $results += [pscustomobject]@{ Step = $step.Name; Status = "FAIL"; Artifact = $path; Notes = $message }
    }
}

$summaryPath = Join-Path $ArtifactDir "walkthrough-summary.md"
$lines = @(
    "# RD2-1 Curated Universe Replay Summary",
    "",
    "Base URL: $BaseUrl",
    "Template: defensive-quality",
    "Curated symbols: $($CuratedSymbols -join ', ')",
    "Top candidate symbols for comparison: $($TopCandidateSymbols -join ', ')",
    "",
    "| Step | Status | Artifact | Notes |",
    "|---|---|---|---|"
)

foreach ($result in $results) {
    $safeNotes = ($result.Notes -replace "\|", "/")
    $lines += "| $($result.Step) | $($result.Status) | `$($result.Artifact)` | $safeNotes |"
}

$lines += ""
$lines += "Decision-support boundary: this replay is workflow evidence only and is not investment advice."
$lines | Set-Content -Path $summaryPath -Encoding utf8

$results | Format-Table -AutoSize
Write-Host ""
Write-Host "RD2-1 summary written to $summaryPath"
