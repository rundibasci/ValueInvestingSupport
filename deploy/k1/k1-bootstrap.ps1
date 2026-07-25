[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')][string]$ProjectId,
    [Parameter(Mandatory)][string]$Region,
    [string]$ServiceName = 'value-investing-support-k1',
    [string]$Repository = 'vis-k1',
    [string]$SqlInstance = 'vis-k1-postgres',
    [string]$DatabaseName = 'vis',
    [string]$DatabaseUser = 'vis_app',
    [string]$RedisInstance = 'vis-k1-redis',
    [string]$VpcConnector = 'vis-k1-connector',
    [string]$Network = 'default'
)

$ErrorActionPreference = 'Stop'

function Invoke-Gcloud {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    & gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud failed: gcloud $($Arguments -join ' ')"
    }
}

function Test-GcloudResource {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & gcloud @Arguments *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required. Install the Google Cloud CLI and authenticate before running this script.'
}

$serviceAccountId = 'vis-k1-runtime'
$serviceAccountEmail = "$serviceAccountId@$ProjectId.iam.gserviceaccount.com"
$requiredApis = @(
    'artifactregistry.googleapis.com',
    'cloudbuild.googleapis.com',
    'run.googleapis.com',
    'sqladmin.googleapis.com',
    'redis.googleapis.com',
    'secretmanager.googleapis.com',
    'vpcaccess.googleapis.com',
    'monitoring.googleapis.com'
)

if ($PSCmdlet.ShouldProcess($ProjectId, 'Enable K1 Google Cloud APIs')) {
    Invoke-Gcloud services enable @requiredApis --project $ProjectId
}

$repositoryExists = Test-GcloudResource artifacts repositories describe $Repository --location $Region --project $ProjectId
if (-not $repositoryExists -and $PSCmdlet.ShouldProcess($Repository, 'Create Artifact Registry repository')) {
    Invoke-Gcloud artifacts repositories create $Repository --repository-format docker --location $Region --project $ProjectId --description 'K1 stakeholder deployment images'
}

$serviceAccountExists = Test-GcloudResource iam service-accounts describe $serviceAccountEmail --project $ProjectId
if (-not $serviceAccountExists -and $PSCmdlet.ShouldProcess($serviceAccountEmail, 'Create K1 runtime service account')) {
    Invoke-Gcloud iam service-accounts create $serviceAccountId --display-name 'VIS K1 Cloud Run runtime' --project $ProjectId
}

foreach ($role in @('roles/cloudsql.client', 'roles/monitoring.metricWriter', 'roles/logging.logWriter')) {
    if ($PSCmdlet.ShouldProcess($serviceAccountEmail, "Grant $role")) {
        Invoke-Gcloud projects add-iam-policy-binding $ProjectId --member "serviceAccount:$serviceAccountEmail" --role $role --condition None
    }
}

$sqlExists = Test-GcloudResource sql instances describe $SqlInstance --project $ProjectId
if (-not $sqlExists -and $PSCmdlet.ShouldProcess($SqlInstance, 'Create non-production Cloud SQL PostgreSQL instance')) {
    Invoke-Gcloud sql instances create $SqlInstance --database-version POSTGRES_16 --edition ENTERPRISE --tier db-f1-micro --region $Region --availability-type zonal --storage-type SSD --storage-size 10 --no-storage-auto-increase --project $ProjectId
}

$databaseExists = Test-GcloudResource sql databases describe $DatabaseName --instance $SqlInstance --project $ProjectId
if (-not $databaseExists -and $PSCmdlet.ShouldProcess($DatabaseName, 'Create application database')) {
    Invoke-Gcloud sql databases create $DatabaseName --instance $SqlInstance --project $ProjectId
}

$databaseUserExists = ((& gcloud sql users list --instance $SqlInstance --project $ProjectId --filter "name=$DatabaseUser" --format 'value(name)') -eq $DatabaseUser)
if (-not $databaseUserExists) {
    Write-Warning "Create database user '$DatabaseUser' manually with a generated password, then store that password in Secret Manager. The script intentionally never receives or prints it."
}

$connectorExists = Test-GcloudResource compute networks vpc-access connectors describe $VpcConnector --region $Region --project $ProjectId
if (-not $connectorExists -and $PSCmdlet.ShouldProcess($VpcConnector, 'Create Serverless VPC Access connector')) {
    Invoke-Gcloud compute networks vpc-access connectors create $VpcConnector --network $Network --region $Region --range '10.8.0.0/28' --min-instances 2 --max-instances 3 --project $ProjectId
}

$redisExists = Test-GcloudResource redis instances describe $RedisInstance --region $Region --project $ProjectId
if (-not $redisExists -and $PSCmdlet.ShouldProcess($RedisInstance, 'Create basic Memorystore Redis instance')) {
    Invoke-Gcloud redis instances create $RedisInstance --size 1 --region $Region --redis-version redis_7_0 --tier basic --network $Network --project $ProjectId
}

$secretNames = @(
    'vis-k1-database-password',
    'vis-k1-fmp-api-key',
    'vis-k1-jwt-private-key',
    'vis-k1-jwt-public-key',
    'vis-k1-smtp-password'
)
foreach ($secretName in $secretNames) {
    $secretExists = Test-GcloudResource secrets describe $secretName --project $ProjectId
    if (-not $secretExists -and $PSCmdlet.ShouldProcess($secretName, 'Create empty Secret Manager secret')) {
        Invoke-Gcloud secrets create $secretName --replication-policy automatic --project $ProjectId
    }
    if ($PSCmdlet.ShouldProcess($serviceAccountEmail, "Grant access to $secretName")) {
        Invoke-Gcloud secrets add-iam-policy-binding $secretName --member "serviceAccount:$serviceAccountEmail" --role roles/secretmanager.secretAccessor --project $ProjectId
    }
}

$connectionName = if (Test-GcloudResource sql instances describe $SqlInstance --project $ProjectId) {
    & gcloud sql instances describe $SqlInstance --project $ProjectId --format 'value(connectionName)'
} else {
    "$ProjectId`:$Region`:$SqlInstance"
}
$redisHost = if (Test-GcloudResource redis instances describe $RedisInstance --region $Region --project $ProjectId) {
    & gcloud redis instances describe $RedisInstance --region $Region --project $ProjectId --format 'value(host)'
} else {
    '<available-after-resource-creation>'
}

[pscustomobject]@{
    ProjectId = $ProjectId
    Region = $Region
    ServiceName = $ServiceName
    RuntimeServiceAccount = $serviceAccountEmail
    CloudSqlInstance = $connectionName
    DatabaseName = $DatabaseName
    DatabaseUser = $DatabaseUser
    RedisHost = $redisHost
    VpcConnector = $VpcConnector
    NextStep = 'Add pinned secret versions, then run k1-deploy.ps1.'
} | Format-List
