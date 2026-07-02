param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ArtifactDir = "specs/2026-07-02-rd1-agent-1-full-feature-walkthrough/evidence",
    [string]$AdminEmail = "admin@realdemo.local",
    [string]$InvestorEmail = "investor@realdemo.local",
    [string]$Password = "admin",
    [switch]$SkipLiveApi
)

$ErrorActionPreference = "Stop"

$Symbols = @("AAPL", "KO", "JNJ", "PG", "MSFT")
$Steps = @(
    [pscustomobject]@{ Name = "Admin login"; Method = "POST"; Path = "/auth/login"; Role = "admin"; Body = @{ email = $AdminEmail; password = $Password } },
    [pscustomobject]@{ Name = "Investor login"; Method = "POST"; Path = "/auth/login"; Role = "investor"; Body = @{ email = $InvestorEmail; password = $Password } },
    [pscustomobject]@{ Name = "Backend health"; Method = "GET"; Path = "/actuator/health"; Role = "public"; Body = $null },
    [pscustomobject]@{ Name = "Dashboard summary"; Method = "GET"; Path = "/api/v1/dashboard"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Screener conservative evidence"; Method = "POST"; Path = "/api/v1/screener"; Role = "investor"; Body = @{ page = 0; pageSize = 10; sortBy = "valueScore"; sortDirection = "DESC" } },
    [pscustomobject]@{ Name = "AAPL security detail"; Method = "GET"; Path = "/api/v1/securities/AAPL"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "KO review packet"; Method = "GET"; Path = "/api/v1/securities/KO/review"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Watchlist"; Method = "GET"; Path = "/api/v1/watchlist"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Portfolio list"; Method = "GET"; Path = "/api/v1/portfolios"; Role = "investor"; Body = $null },
    [pscustomobject]@{ Name = "Job definitions"; Method = "GET"; Path = "/api/v1/admin/jobs"; Role = "admin"; Body = $null },
    [pscustomobject]@{ Name = "Job run history"; Method = "GET"; Path = "/api/v1/admin/jobs/runs"; Role = "admin"; Body = $null },
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

New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null

if ($SkipLiveApi) {
    $manifest = [pscustomobject]@{
        mode = "dry-run"
        baseUrl = $BaseUrl
        symbols = $Symbols
        steps = $Steps | ForEach-Object { [pscustomobject]@{ name = $_.Name; method = $_.Method; path = $_.Path; role = $_.Role } }
        note = "Dry run only. Start docker compose -f docker-compose.realDemo.yml up --build and rerun without -SkipLiveApi for live evidence."
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content -Path (Join-Path $ArtifactDir "dry-run-manifest.json") -Encoding utf8
    Write-Host "RD1-2 dry-run manifest written to $ArtifactDir"
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

        $redacted = $response | ConvertTo-Json -Depth 20
        $redacted = $redacted -replace '"accessToken"\s*:\s*"[^"]+"', '"accessToken":"<redacted>"'
        $redacted = $redacted -replace '"refreshToken"\s*:\s*"[^"]+"', '"refreshToken":"<redacted>"'
        $redacted | Set-Content -Path $path -Encoding utf8

        $results += [pscustomobject]@{ Step = $step.Name; Status = "PASS"; Artifact = $path; Notes = "" }
    } catch {
        $message = $_.Exception.Message
        [pscustomobject]@{ error = $message; step = $step.Name; path = $step.Path } |
            ConvertTo-Json -Depth 8 |
            Set-Content -Path $path -Encoding utf8
        $results += [pscustomobject]@{ Step = $step.Name; Status = "FAIL"; Artifact = $path; Notes = $message }
    }
}

$summaryPath = Join-Path $ArtifactDir "walkthrough-summary.md"
$lines = @(
    "# RD1-2 Live Replay Summary",
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
$lines += "Decision-support boundary: this replay is workflow evidence only and is not investment advice."
$lines | Set-Content -Path $summaryPath -Encoding utf8

$results | Format-Table -AutoSize
Write-Host ""
Write-Host "RD1-2 summary written to $summaryPath"
