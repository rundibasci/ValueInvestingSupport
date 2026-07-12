param(
    [string]$TaskName = "VIS-Chart-Bug-Fix-Agent",
    [int]$MaxCycles = 10,
    [int]$IntervalHours = 3
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path ".").Path
$cycleScript = Join-Path $repoRoot "scripts/chart-bug-fix-cycle.ps1"
if (-not (Test-Path -LiteralPath $cycleScript)) {
    throw "Missing cycle script: $cycleScript"
}

$codex = Get-Command codex -ErrorAction SilentlyContinue
$codexArgument = if ($codex) { " -CodexCommand `"$($codex.Source)`"" } else { "" }
$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$cycleScript`" -RunOnce -NoDelay -MaxCycles 1$codexArgument"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argument -WorkingDirectory $repoRoot
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Hours $IntervalHours) -RepetitionDuration (New-TimeSpan -Hours ($IntervalHours * $MaxCycles))
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Force | Out-Null

Write-Host "Registered scheduled task $TaskName."
Write-Host "It starts once and repeats every $IntervalHours hours for up to $MaxCycles scheduled cycles."
