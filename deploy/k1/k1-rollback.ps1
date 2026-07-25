[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [Parameter(Mandatory)][string]$Revision,
    [string]$ServiceName = 'value-investing-support-k1'
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required. Install the Google Cloud CLI and authenticate before running this script.'
}

$revisionExists = & gcloud run revisions describe $Revision --region $Region --project $ProjectId --format 'value(metadata.name)' 2>$null
if ($LASTEXITCODE -ne 0 -or -not $revisionExists) {
    throw "Revision '$Revision' not found in region '$Region' for project '$ProjectId'."
}

if ($PSCmdlet.ShouldProcess($ServiceName, "Rollback to revision $Revision")) {
    & gcloud run services update-traffic $ServiceName --region $Region --project $ProjectId --to-revisions "${Revision}=100"
    if ($LASTEXITCODE -ne 0) {
        throw "Rollback to revision '$Revision' failed."
    }
}

$serviceUrl = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(status.url)'
$traffic = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'json(status.traffic)'

Write-Host "Rollback complete. Service URL: $serviceUrl"
Write-Host "Traffic configuration: $traffic"
