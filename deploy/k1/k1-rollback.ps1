[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [Parameter(Mandatory)][string]$Revision,
    [string]$ServiceName = 'value-investing-support-k1'
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required.'
}

$revisionService = & gcloud run revisions describe $Revision --region $Region --project $ProjectId --format 'value(metadata.labels.serving_knative_dev/service)'
if ($LASTEXITCODE -ne 0 -or $revisionService -ne $ServiceName) {
    throw "Revision '$Revision' does not belong to service '$ServiceName'."
}

if ($PSCmdlet.ShouldProcess($ServiceName, "Route all traffic to $Revision")) {
    & gcloud run services update-traffic $ServiceName --region $Region --project $ProjectId --to-revisions "$Revision=100"
    if ($LASTEXITCODE -ne 0) {
        throw 'Cloud Run traffic rollback failed.'
    }
}

& gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'yaml(status.url,status.traffic,status.latestReadyRevisionName)'
