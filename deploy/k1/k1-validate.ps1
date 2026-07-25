[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [string]$ServiceName = 'value-investing-support-k1',
    [switch]$UseIdentityToken
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required. Install the Google Cloud CLI and authenticate before running this script.'
}

$serviceUrl = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(status.url)' 2>$null
if ($LASTEXITCODE -ne 0 -or -not $serviceUrl) {
    throw "Cloud Run service '$ServiceName' not found in region '$Region' for project '$ProjectId'."
}

$revision = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(status.latestReadyRevisionName)'
$maxInstances = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(template.scaling.max-instances)'

Write-Host "Service: $ServiceName"
Write-Host "URL: $serviceUrl"
Write-Host "Revision: $revision"
Write-Host "Max Instances: $maxInstances"
Write-Host ""

# Prepare auth header
$curlArgs = @('-s', '-o', 'k1-validation-health.json', '-w', '%{http_code}')
if ($UseIdentityToken) {
    $token = & gcloud auth print-identity-token
    if ($LASTEXITCODE -ne 0) { throw 'Failed to obtain identity token.' }
    $curlArgs += '-H', "Authorization: Bearer $token"
}

# Health check
Write-Host "--- Health check ---"
$healthUrl = "$serviceUrl/actuator/health"
$httpCode = (& curl @curlArgs $healthUrl)
Write-Host "GET $healthUrl -> $httpCode"
if ($httpCode -ne '200') {
    Write-Error "Health check failed with HTTP $httpCode."
    $healthBody = Get-Content k1-validation-health.json -Raw -ErrorAction SilentlyContinue
    Write-Host "Response body: $healthBody"
    exit 1
}
$healthBody = Get-Content k1-validation-health.json -Raw | ConvertFrom-Json
Write-Host "Status: $($healthBody.status)"
Write-Host ""

# Revision check
Write-Host "--- Revision check ---"
$revisionMaxInstances = & gcloud run revisions describe $revision --region $Region --project $ProjectId --format 'value(containers.resources.maxInstances)' 2>$null
if ($revisionMaxInstances) {
    Write-Host "Revision max instances: $revisionMaxInstances"
}
if ($maxInstances -ne '1') {
    Write-Error "Service max-instances is $maxInstances, expected 1."
    exit 1
}
Write-Host "Max instances check: PASS"
Write-Host ""

# Structured logs check
Write-Host "--- Cloud Logging check ---"
$logEntries = & gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=$ServiceName AND severity>=INFO" --limit 3 --project $ProjectId --format 'value(timestamp)' 2>$null
if ($LASTEXITCODE -eq 0 -and $logEntries) {
    Write-Host "Structured logs: PRESENT ($($logEntries.Count) recent entries found)"
} else {
    Write-Warning "No recent log entries found; may take a few minutes after deployment."
}
Write-Host ""

# Summary
Write-Host "=== K1 Validation Summary ==="
Write-Host "Service URL:  $serviceUrl"
Write-Host "Revision:     $revision"
Write-Host "Max Instances: $maxInstances"
Write-Host "Health:       $($healthBody.status)"
Write-Host "==========================="

Remove-Item k1-validation-health.json -ErrorAction SilentlyContinue
