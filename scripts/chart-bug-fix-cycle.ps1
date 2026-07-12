param(
    [string]$BugFile = "specs/chart-qa-agent/bugs.json",
    [string]$AuditScript = "scripts/chart-qa-agent.ps1",
    [string]$PromptDir = "specs/chart-qa-agent/fix-prompts",
    [string]$LogDir = "specs/chart-qa-agent/fix-logs",
    [string]$CodexCommand = $env:VIS_CODEX_AGENT_COMMAND,
    [int]$MaxCycles = 10,
    [int]$IntervalHours = 3,
    [switch]$RunOnce,
    [switch]$NoDelay,
    [switch]$PromptOnly
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path (Get-Location) $Path
}

function Read-BugRegistry([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{ generatedAt = (Get-Date).ToString("o"); bugs = @() }
    }

    $raw = Get-Content -LiteralPath $Path -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return [pscustomobject]@{ generatedAt = (Get-Date).ToString("o"); bugs = @() }
    }

    return $raw | ConvertFrom-Json
}

function Write-BugRegistry([string]$Path, [object]$Registry) {
    $parent = Split-Path -Parent $Path
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $Registry.generatedAt = (Get-Date).ToString("o")
    $Registry | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Write-CycleLog([string]$Path, [string]$Message) {
    $line = "{0} {1}" -f (Get-Date).ToString("o"), $Message
    Add-Content -LiteralPath $Path -Value $line -Encoding UTF8
}

function Get-NextBug([object]$Registry) {
    $openBugs = @($Registry.bugs | Where-Object { $_.status -in @("open", "reopened") } | Sort-Object lastSeenAt)
    if ($openBugs.Count -eq 0) { return $null }
    return $openBugs[0]
}

function Set-JsonProperty([object]$Target, [string]$Name, [object]$Value) {
    if ($Target.PSObject.Properties.Name -contains $Name) {
        $Target.$Name = $Value
    } else {
        $Target | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

function Update-BugState([object]$Registry, [string]$BugId, [string]$Status, [string]$Note) {
    foreach ($bug in @($Registry.bugs)) {
        if ($bug.id -eq $BugId) {
            $attemptCount = 0
            if ($null -ne $bug.fixAttemptCount) { $attemptCount = [int]$bug.fixAttemptCount }
            $bug.status = $Status
            Set-JsonProperty $bug "lastFixAttemptAt" (Get-Date).ToString("o")
            Set-JsonProperty $bug "fixAttemptCount" (1 + $attemptCount)
            Set-JsonProperty $bug "lastFixNote" $Note
        }
    }
}

function New-FixPrompt([object]$Bug, [int]$CycleNumber, [string]$OutDir) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $path = Join-Path $OutDir ("cycle-{0:00}-{1}.md" -f $CycleNumber, $Bug.id)
    $json = $Bug | ConvertTo-Json -Depth 20
    $prompt = @"
You are Codex running inside the ValueInvestingSupport repository.

Goal: fix exactly one persisted chart defect, then verify it.

Bug to fix:
~~~json
$json
~~~

Constraints:
- Keep the change focused on this bug.
- Do not revert unrelated user changes.
- Prefer existing project patterns.
- Run the most relevant tests or checks you can.
- After editing, run:
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/chart-qa-agent.ps1
- Leave specs/chart-qa-agent/bugs.json updated by that audit.
- Commit only if the repository convention or current user explicitly asks for it; otherwise leave changes staged/unstaged for review.
"@
    $prompt | Set-Content -LiteralPath $path -Encoding UTF8
    return $path
}

if (-not $CodexCommand) {
    $cmd = Get-Command codex -ErrorAction SilentlyContinue
    if ($cmd) { $CodexCommand = $cmd.Source }
}

$BugFilePath = Resolve-RepoPath $BugFile
$PromptDirPath = Resolve-RepoPath $PromptDir
$LogDirPath = Resolve-RepoPath $LogDir
New-Item -ItemType Directory -Force -Path $PromptDirPath, $LogDirPath | Out-Null

for ($cycle = 1; $cycle -le $MaxCycles; $cycle++) {
    $registry = Read-BugRegistry $BugFilePath
    $bug = Get-NextBug $registry
    if (-not $bug) {
        Write-Host "No open/reopened chart bugs found. Stopping at cycle $cycle."
        break
    }

    Update-BugState $registry $bug.id "in_progress" "Cycle $cycle started."
    Write-BugRegistry $BugFilePath $registry

    $promptPath = New-FixPrompt -Bug $bug -CycleNumber $cycle -OutDir $PromptDirPath
    $logPath = Join-Path $LogDirPath ("cycle-{0:00}-{1}.log" -f $cycle, $bug.id)
    $runnerLogPath = Join-Path $LogDirPath ("cycle-{0:00}-{1}-runner.log" -f $cycle, $bug.id)
    Write-CycleLog $runnerLogPath "Starting cycle $cycle for $($bug.id). Prompt=$promptPath"

    if ($PromptOnly) {
        $registry = Read-BugRegistry $BugFilePath
        Update-BugState $registry $bug.id "open" "Prompt-only cycle wrote $promptPath."
        Write-BugRegistry $BugFilePath $registry
        Write-CycleLog $runnerLogPath "Prompt-only mode completed."
        Write-Host "Prompt-only mode wrote $promptPath."
    } elseif (-not $CodexCommand) {
        $registry = Read-BugRegistry $BugFilePath
        Update-BugState $registry $bug.id "open" "Codex command not found. Prompt written to $promptPath."
        Write-BugRegistry $BugFilePath $registry
        Write-CycleLog $runnerLogPath "Codex command not found."
        Write-Warning "Codex command not found. Prompt written to $promptPath."
    } else {
        Write-Host "Cycle $cycle fixing $($bug.id) with Codex. Log: $logPath"
        Write-CycleLog $runnerLogPath "Invoking CodexCommand=$CodexCommand"
        try {
            $cmdLine = '"{0}" exec --cd "{1}" --dangerously-bypass-approvals-and-sandbox - < "{2}" > "{3}" 2>&1' -f $CodexCommand, (Get-Location).Path, $promptPath, $logPath
            & cmd.exe /d /c $cmdLine
            Write-CycleLog $runnerLogPath "Codex exit code: $LASTEXITCODE"
        } catch {
            Write-CycleLog $runnerLogPath "Codex invocation threw: $($_.Exception.Message)"
            throw
        }
        if ($LASTEXITCODE -ne 0) {
            $registry = Read-BugRegistry $BugFilePath
            Update-BugState $registry $bug.id "open" "Codex exited with code $LASTEXITCODE. See $logPath."
            Write-BugRegistry $BugFilePath $registry
        }
    }

    if (-not $PromptOnly) {
        Write-CycleLog $runnerLogPath "Running audit script."
        powershell -NoProfile -ExecutionPolicy Bypass -File $AuditScript
        Write-CycleLog $runnerLogPath "Audit script completed with code $LASTEXITCODE."
    }
    $postRegistry = Read-BugRegistry $BugFilePath
    $postBug = @($postRegistry.bugs | Where-Object { $_.id -eq $bug.id })[0]
    if ($postBug -and $postBug.status -in @("open", "reopened")) {
        Update-BugState $postRegistry $bug.id "open" "Audit still detects the bug after cycle $cycle."
    } elseif ($postBug) {
        Update-BugState $postRegistry $bug.id "fixed" "Audit did not reopen this bug after cycle $cycle."
    }
    Write-BugRegistry $BugFilePath $postRegistry
    Write-CycleLog $runnerLogPath "Cycle $cycle registry update completed."

    if ($RunOnce) { break }
    if ($cycle -lt $MaxCycles -and -not $NoDelay) {
        Start-Sleep -Seconds ([Math]::Max(1, $IntervalHours) * 3600)
    }
}
