[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [string]$ServiceName = 'value-investing-support-k1',
    [switch]$UseIdentityToken
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required.'
}

$serviceJson = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to describe the Cloud Run service.'
}

$serviceMax = $serviceJson.scaling.maxInstanceCount
if (-not $serviceMax) {
    $serviceMax = $serviceJson.metadata.annotations.'run.googleapis.com/maxScale'
}
$revisionMax = $serviceJson.spec.template.metadata.annotations.'autoscaling.knative.dev/maxScale'
if ([string]$serviceMax -ne '1' -or [string]$revisionMax -ne '1') {
    throw "K1 scheduling safety failed: maximum instances is not 1 (service=$serviceMax, revision=$revisionMax)."
}

$url = $serviceJson.status.url
$headers = @{}
if ($UseIdentityToken) {
    $token = (& gcloud auth print-identity-token).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $token) {
        throw 'Unable to obtain an identity token.'
    }
    $headers.Authorization = "Bearer $token"
}

$health = Invoke-RestMethod -Uri "$url/actuator/health" -Headers $headers -TimeoutSec 30
if ($health.status -notin @('UP', 'DEGRADED')) {
    throw "Health status is '$($health.status)'."
}

$index = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 30
if ($index.StatusCode -ne 200 -or $index.Content -notmatch '<div id="root">') {
    throw 'Bundled React application smoke test failed.'
}

[pscustomobject]@{
    Service = $ServiceName
    Url = $url
    Revision = $serviceJson.status.latestReadyRevisionName
    Health = $health.status
    ReactApp = 'PASS'
    MaxInstances = 1
} | Format-List
