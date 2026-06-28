param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "prudent.beta@localstack.local",
    [string]$Password = "PersonaDemo123!"
)

$ErrorActionPreference = "Stop"

$Symbols = @("BRK.B", "JNJ", "PG", "KO", "PEP", "WMT", "MSFT", "ADP", "UNP", "XOM")

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
        $args.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    Invoke-RestMethod @args
}

Write-Host "I1 persona replay: Agent 1 prudent-value workflow"
Write-Host "Base URL: $BaseUrl"
Write-Host "Symbols: $($Symbols -join ',')"
Write-Host ""

$login = Invoke-Json -Method "POST" -Path "/auth/login" -Body @{ email = $Email; password = $Password }
$token = $login.accessToken
if (-not $token) {
    throw "Login did not return accessToken. Confirm the local demo persona user exists."
}

Write-Host "Authenticated as $Email"

$rows = foreach ($symbol in $Symbols) {
    try {
        $review = Invoke-Json -Method "GET" -Path "/api/v1/securities/$symbol/review" -Token $token
        [pscustomobject]@{
            Symbol = $symbol
            ScoreStatus = ($review.availability | Where-Object { $_.category -eq "Score" }).state.status
            ValuationStatus = ($review.availability | Where-Object { $_.category -eq "Valuation" }).state.status
            QuoteStatus = ($review.availability | Where-Object { $_.category -eq "Quote" }).state.status
            MarginOfSafety = $review.valuation.marginOfSafety
            Recommendation = $review.valuation.recommendation
            DataQualityNotes = ($review.dataQualityNotes | ForEach-Object { "$($_.category):$($_.severity)" }) -join "; "
        }
    } catch {
        [pscustomobject]@{
            Symbol = $symbol
            ScoreStatus = "ERROR"
            ValuationStatus = "ERROR"
            QuoteStatus = "ERROR"
            MarginOfSafety = $null
            Recommendation = $null
            DataQualityNotes = $_.Exception.Message
        }
    }
}

$rows | Format-Table -AutoSize

Write-Host ""
Write-Host "Replay interpretation boundary: this output is deterministic workflow evidence only; it is not investment advice or an investable model portfolio."
