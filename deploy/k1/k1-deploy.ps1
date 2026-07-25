[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [Parameter(Mandatory)][string]$CloudSqlInstance,
    [Parameter(Mandatory)][string]$RedisHost,
    [string]$ServiceName = 'value-investing-support-k1',
    [string]$Repository = 'vis-k1',
    [string]$RuntimeServiceAccount = "vis-k1-runtime@$ProjectId.iam.gserviceaccount.com",
    [string]$VpcConnector = 'vis-k1-connector',
    [string]$DatabaseName = 'vis',
    [string]$DatabaseUser = 'vis_app',
    [string]$MarketDataSource = 'fmp',
    [ValidateSet('application', 'cloud-run-iam')][string]$AccessMode = 'application',
    [string]$ImageTag
)

$ErrorActionPreference = 'Stop'

function Invoke-Gcloud {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    & gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud failed: gcloud $($Arguments -join ' ')"
    }
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required. Install the Google Cloud CLI and authenticate before running this script.'
}
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\Dockerfile'))) {
    throw 'Run this script from a complete repository checkout; the root Dockerfile is missing.'
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not $ImageTag) {
    $ImageTag = (& git -C $repoRoot rev-parse --short=12 HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $ImageTag) {
        throw 'Unable to derive an immutable image tag from Git.'
    }
}
if ($ImageTag -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'ImageTag contains unsupported characters.'
}

$image = "$Region-docker.pkg.dev/$ProjectId/$Repository/vis:$ImageTag"
$secretBindings = @(
    'DATABASE_PASSWORD=vis-k1-database-password:1',
    'FMP_API_KEY=vis-k1-fmp-api-key:1',
    'JWT_PRIVATE_KEY=vis-k1-jwt-private-key:1',
    'JWT_PUBLIC_KEY=vis-k1-jwt-public-key:1',
    'SPRING_MAIL_PASSWORD=vis-k1-smtp-password:1'
) -join ','
$environment = @(
    'SPRING_PROFILES_ACTIVE=k1',
    "CLOUD_SQL_INSTANCE=$CloudSqlInstance",
    "DATABASE_NAME=$DatabaseName",
    "DATABASE_USERNAME=$DatabaseUser",
    "REDIS_HOST=$RedisHost",
    'REDIS_PORT=6379',
    "MARKET_DATA_SOURCE=$MarketDataSource",
    'JOBS_ENABLED=true',
    'ALERT_EMAIL_ENABLED=false',
    'REQUEST_LOGGING_ENABLED=true',
    'TRACING_SAMPLING_PROBABILITY=0.1'
) -join ','

foreach ($secretName in @('vis-k1-database-password', 'vis-k1-fmp-api-key', 'vis-k1-jwt-private-key', 'vis-k1-jwt-public-key', 'vis-k1-smtp-password')) {
    $version = & gcloud secrets versions describe 1 --secret $secretName --project $ProjectId --format 'value(state)' 2>$null
    if ($LASTEXITCODE -ne 0 -or $version -ne 'ENABLED') {
        throw "Secret '$secretName' must have enabled version 1 before deployment."
    }
}

if ($PSCmdlet.ShouldProcess($image, 'Build and publish immutable K1 image')) {
    Push-Location $repoRoot
    try {
        Invoke-Gcloud builds submit --tag $image --project $ProjectId .
    } finally {
        Pop-Location
    }
}

$accessFlag = if ($AccessMode -eq 'application') { '--allow-unauthenticated' } else { '--no-allow-unauthenticated' }
if ($PSCmdlet.ShouldProcess($ServiceName, "Deploy K1 Cloud Run revision $ImageTag")) {
    Invoke-Gcloud run deploy $ServiceName `
        --image $image `
        --region $Region `
        --project $ProjectId `
        --service-account $RuntimeServiceAccount `
        --execution-environment gen2 `
        --port 8080 `
        --cpu 1 `
        --memory 1Gi `
        --concurrency 20 `
        --timeout 300 `
        --min-instances 1 `
        --max-instances 1 `
        --no-cpu-throttling `
        --startup-probe 'httpGet.path=/actuator/health,httpGet.port=8080,initialDelaySeconds=10,timeoutSeconds=5,periodSeconds=10,failureThreshold=12' `
        --liveness-probe 'httpGet.path=/actuator/health/liveness,httpGet.port=8080,initialDelaySeconds=30,timeoutSeconds=5,periodSeconds=30,failureThreshold=3' `
        --vpc-connector $VpcConnector `
        --vpc-egress private-ranges-only `
        --set-env-vars $environment `
        --set-secrets $secretBindings `
        $accessFlag
}

Invoke-Gcloud run services update $ServiceName --region $Region --project $ProjectId --max 1

$serviceUrl = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(status.url)'
$revision = & gcloud run services describe $ServiceName --region $Region --project $ProjectId --format 'value(status.latestReadyRevisionName)'

[pscustomobject]@{
    Service = $ServiceName
    Url = $serviceUrl
    Revision = $revision
    Image = $image
    AccessMode = $AccessMode
    MaxInstances = 1
} | Format-List
