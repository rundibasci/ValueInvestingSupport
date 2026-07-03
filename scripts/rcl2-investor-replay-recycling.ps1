param(
    [string]$CycleId = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$OutputRoot = "specs/2026-07-03-rcl2-investor-replay-recycling/evidence",
    [string]$FrontendBaseUrl = "http://localhost:5173",
    [string]$BackendBaseUrl = "http://localhost:8080",
    [switch]$SkipLiveApi
)

$ErrorActionPreference = "Stop"

$cycleDir = Join-Path $OutputRoot $CycleId
New-Item -ItemType Directory -Force -Path $cycleDir | Out-Null

$timestamp = (Get-Date).ToString("o")
$routes = @(
    "$FrontendBaseUrl/watchlist",
    "$FrontendBaseUrl/screener",
    "$FrontendBaseUrl/securities/KO",
    "$FrontendBaseUrl/securities/KO/review"
)

$probes = @()
if (-not $SkipLiveApi) {
    foreach ($path in @("/actuator/health", "/api/v1/screener")) {
        $method = if ($path -eq "/api/v1/screener") { "POST" } else { "GET" }
        $uri = "$BackendBaseUrl$path"
        try {
            if ($method -eq "POST") {
                $response = Invoke-WebRequest -Method Post -Uri $uri -ContentType "application/json" -Body "{}" -UseBasicParsing
            } else {
                $response = Invoke-WebRequest -Method Get -Uri $uri -UseBasicParsing
            }
            $probes += [ordered]@{
                method = $method
                uri = $uri
                status = [int]$response.StatusCode
                ok = $true
            }
        } catch {
            $statusCode = $null
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            $probes += [ordered]@{
                method = $method
                uri = $uri
                status = $statusCode
                ok = $false
                error = $_.Exception.Message
            }
        }
    }
}

$manifest = [ordered]@{
    cycleId = $CycleId
    generatedAt = $timestamp
    mode = if ($SkipLiveApi) { "dry-run" } else { "live-probe" }
    frontendBaseUrl = $FrontendBaseUrl
    backendBaseUrl = $BackendBaseUrl
    investorAgentRoutes = $routes
    requiredArtifacts = @(
        "investor-report.md",
        "monitor-report.md",
        "triage-backlog.md",
        "decision-support-boundary.md"
    )
    liveProbes = $probes
    gate = [ordered]@{
        requiresTwoConsecutiveCleanCycles = $true
        blocksOnHighOrMediumSeverity = $true
        blocksOnUnexplainedBackend5xx = $true
        blocksOnFrontendConsoleError = $true
        blocksOnRawAuthorizationFailure = $true
        blocksOnDataQualityContradiction = $true
    }
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $cycleDir "manifest.json") -Encoding UTF8

@"
# Investor Agent Report - $CycleId

Generated: $timestamp
Mode: $(if ($SkipLiveApi) { "dry-run" } else { "live-probe" })

## Routes To Exercise

$($routes | ForEach-Object { "- $_" } | Out-String)

## Required Investor Observations

- Record route, action, visible result, screenshot path, and request payload when available.
- Describe model evidence and data availability, not investment advice.
- Flag contradictions between screener, comparison, watchlist, portfolio, and review packet data.
- Mark any raw `Forbidden`, unexplained blank state, chart warning, or missing feedback as a finding.

## Shortlist Boundary

Any shortlist produced during this replay is a validation artifact for product behavior. It is not investable, personalized, or a recommendation to trade.

## Findings

No live investor findings recorded in this generated baseline. Add findings to `triage-backlog.md` during live replay.
"@ | Set-Content -Path (Join-Path $cycleDir "investor-report.md") -Encoding UTF8

$probeSummary = if ($probes.Count -eq 0) {
    "Live probes skipped."
} else {
    ($probes | ForEach-Object { "- $($_.method) $($_.uri) => status=$($_.status), ok=$($_.ok)" }) -join [Environment]::NewLine
}

@"
# Monitor Agent Report - $CycleId

Generated: $timestamp
Mode: $(if ($SkipLiveApi) { "dry-run" } else { "live-probe" })

## Probe Summary

$probeSummary

## Log Correlation Checklist

- Backend application log excerpt for each finding.
- Frontend console warning/error, if visible.
- Docker/container status where relevant.
- Correlation ID or timestamp window.
- HTTP method, route/API, status code, payload class, and user role.

## Severity Rules

- High: data corruption, misleading decision-support output, security/access control exposure, or repeated unexplained `5xx`.
- Medium: blocked workflow, missing user feedback, raw authorization failure, data-quality contradiction, or accessibility issue affecting core flows.
- Low: copy polish, minor layout issue, nice-to-have diagnostics, or accepted provider limitation.
"@ | Set-Content -Path (Join-Path $cycleDir "monitor-report.md") -Encoding UTF8

@"
# Triage Backlog - $CycleId

| ID | Severity | Category | Status | Owner | Route/API | Reproduction Path | Investor Observation | Monitor Evidence | Decision | Target Phase |
|---|---|---|---|---|---|---|---|---|---|---|
| RCL2-$CycleId-001 | Low | product follow-up | Accepted Risk | RCL2 | Generated baseline | Run dry-run generator | Baseline artifact structure only; no live issue claimed. | `manifest.json`, `investor-report.md`, `monitor-report.md` generated. | Accept as protocol smoke evidence. | RCL2 |

## Gate Result

Dry-run protocol gate is structurally complete. Live gate remains pending until investor and monitor agents execute against the running real-demo stack.
"@ | Set-Content -Path (Join-Path $cycleDir "triage-backlog.md") -Encoding UTF8

@"
# Decision-Support Boundary - $CycleId

This replay cycle validates product behavior, data availability, and workflow clarity. It does not produce personalized advice, trade recommendations, or an investable portfolio.

Reports may use terms such as model evidence, valuation classification, data-quality status, and workflow finding. Reports must not say buy, sell, hold, investable, or recommended unless quoting existing UI text as a compliance-sensitive finding.
"@ | Set-Content -Path (Join-Path $cycleDir "decision-support-boundary.md") -Encoding UTF8

Write-Host "RCL2 replay artifacts written to $cycleDir"
