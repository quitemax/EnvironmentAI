<#
.SYNOPSIS
  Summarises an EnvironmentAI training log (system1-*.jsonl and its system2-*.jsonl sibling).

.DESCRIPTION
  Answers the questions that kept coming up while tuning, in one run: did the new build load, what
  did the model decide and how often did rules or overrides replace it, how long did calls take and
  what slowed them, and did fleeing actually open the distance.

  Defaults to the newest log in run/environmentai/training.

.PARAMETER Path
  A system1-*.jsonl file. The system2-* file with the same timestamp is picked up automatically.

.PARAMETER Mob
  Short id (first 6 characters of a mob uuid) to print a full timeline for.

.EXAMPLE
  .\tools\analyze-log.ps1
  .\tools\analyze-log.ps1 -Path run\environmentai\training\system1-20261011-184202.jsonl
#>
param(
	[string]$Path,
	[string]$Mob
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Path) {
	$Path = (Get-ChildItem (Join-Path $root 'run\environmentai\training') -Filter 'system1-*.jsonl' |
		Sort-Object LastWriteTime | Select-Object -Last 1).FullName
}
if (-not $Path -or -not (Test-Path $Path)) { throw "No system1-*.jsonl log found. Pass -Path." }

function Read-Jsonl([string]$File) {
	if (-not (Test-Path $File)) { return @() }
	Get-Content $File -Encoding UTF8 | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }
}

function Percentile($values, [double]$p) {
	$sorted = @($values | Where-Object { $null -ne $_ } | Sort-Object)
	if ($sorted.Count -eq 0) { return 'n/a' }
	$sorted[[math]::Min($sorted.Count - 1, [int]($sorted.Count * $p))]
}

function Has($row, [string]$name) { $row.PSObject.Properties.Name -contains $name }

$s2Path = $Path -replace 'system1-', 'system2-'
$rows = @(Read-Jsonl $Path)
$s2 = @(Read-Jsonl $s2Path)
if ($rows.Count -eq 0) { "Log is empty: $Path"; return }

$model = @($rows | Where-Object source -eq 'model')
$t0 = ($rows.dispatched_at_ms | Measure-Object -Minimum).Minimum
$span = [math]::Round((($rows.dispatched_at_ms | Measure-Object -Maximum).Maximum - $t0) / 1000)

"=== $(Split-Path -Leaf $Path)"
"rows=$($rows.Count) model=$($model.Count) span=${span}s  system2 rows=$($s2.Count)"
"models: " + (($rows | Group-Object model | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
"sources: " + (($rows | Group-Object source | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
"mobs: " + (($rows | Group-Object mob_type | ForEach-Object { "$($_.Name -replace 'minecraft:','')=$($_.Count)" }) -join ', ') +
	"  (unique: $(($rows.mob_uuid | Sort-Object -Unique).Count))"

""
"--- Which build ran (look for the features you expect)"
"health label in prompt:      $(@($model | Where-Object { $_.prompt -match '\((healthy|wounded|CRITICAL)\)' }).Count)/$($model.Count)"
"nearby-creatures line:       $(@($model | Where-Object { $_.prompt -match 'Nearby creatures' }).Count)/$($model.Count)"
"target gear line:            $(@($model | Where-Object { $_.prompt -match 'Target gear:' }).Count)/$($model.Count)"
"decimal comma in prompt:     $(@($model | Where-Object { $_.prompt -match '\d,\d blocks' }).Count)  (should be 0)"

""
"--- Decisions: action by mob health band (model rows)"
$model | ForEach-Object {
	[pscustomobject]@{
		band = $(if ($_.health / $_.max_health -lt 0.25) { '1 CRITICAL <25%' } elseif ($_.health / $_.max_health -lt 0.6) { '2 wounded' } else { '3 healthy' })
		a = $_.action
	}
} | Group-Object band, a | Sort-Object Name | ForEach-Object { "{0,-34} {1}" -f $_.Name, $_.Count }

""
"--- Action by mob type (model rows)"
$model | Group-Object mob_type, action | Sort-Object Name | ForEach-Object { "{0,-44} {1}" -f ($_.Name -replace 'minecraft:', ''), $_.Count }

""
"--- Rules and overrides (these replaced the model's answer)"
$ruleRows = @($rows | Where-Object source -eq 'rule')
"rule rows: " + $(if ($ruleRows.Count) { (($ruleRows | Group-Object rule | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ') } else { 'none' })
$ov = @($rows | Where-Object { Has $_ 'override' })
"overrides: " + $(if ($ov.Count) { (($ov | Group-Object override, { $_.raw_response -replace '\s', '' } | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join '; ') } else { 'none' })
$inv = @($rows | Where-Object { Has $_ 'invalid_action' })
"invalid actions rejected: $($inv.Count)" + $(if ($inv.Count) { " (" + (($inv | Group-Object invalid_action | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ') + ")" })

""
"--- Latency (System 1 model rows)"
"response_ms  p50=$(Percentile $model.response_ms 0.5)  p90=$(Percentile $model.response_ms 0.9)  max=$(($model.response_ms | Measure-Object -Maximum).Maximum)"
"apply_delay  p50=$(Percentile $model.apply_delay_ms 0.5)  max=$(($model.apply_delay_ms | Measure-Object -Maximum).Maximum)"
"by own in-flight count:"
$model | Group-Object in_flight_at_dispatch | Sort-Object { [int]$_.Name } | ForEach-Object {
	"   in_flight={0} n={1} p50={2}" -f $_.Name, $_.Count, (Percentile $_.Group.response_ms 0.5)
}
"by System 2 in flight (shared GPU?):"
$model | Where-Object { Has $_ 'system2_in_flight' } | Group-Object system2_in_flight | Sort-Object { [int]$_.Name } | ForEach-Object {
	"   system2_in_flight={0} n={1} p50={2}" -f $_.Name, $_.Count, (Percentile $_.Group.response_ms 0.5)
}

$errs = @($rows | Where-Object source -eq 'error')
""
"--- Errors: $($errs.Count)"
foreach ($e in $errs | Select-Object -First 6) {
	"   t={0}s response_ms={1} in_flight={2}  {3}" -f [math]::Round(($e.dispatched_at_ms - $t0) / 1000, 1), $e.response_ms, $e.in_flight_at_dispatch, $e.error
}
if ($errs.Count) { "   (errors bunched in the first seconds are startup load, not steady state)" }

""
"--- Does fleeing open the distance? (rule heartbeat rows, target distance in order)"
$heart = @($rows | Where-Object { $_.rule -like '*_sustained' -or $_.rule -in 'critical_flee', 'intimidated' })
if ($heart.Count) {
	$heart | Group-Object mob_uuid | Select-Object -First 8 | ForEach-Object {
		$g = $_.Group | Sort-Object dispatched_at_ms
		"{0} [{1}] {2}: {3}" -f $_.Name.Substring(0, 6), ($g[0].mob_type -replace 'minecraft:', ''), $g[0].rule,
			(($g | ForEach-Object { [math]::Round($_.target_distance, 1) }) -join ' -> ')
	}
} else { "none" }

if ($s2.Count) {
	$m2 = @($s2 | Where-Object source -eq 'model')
	""
	"=== System 2: calls=$($s2.Count) model=$($m2.Count) errors=$(@($s2 | Where-Object source -eq 'error').Count) models: " + (($s2 | Group-Object model | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
	"response_ms     p50=$(Percentile $m2.response_ms 0.5)  p90=$(Percentile $m2.response_ms 0.9)  max=$(($m2.response_ms | Measure-Object -Maximum).Maximum)"
	"trigger_wait   p50=$(Percentile $s2.trigger_wait_ms 0.5)  p90=$(Percentile $s2.trigger_wait_ms 0.9)  max=$(($s2.trigger_wait_ms | Measure-Object -Maximum).Maximum)"
	"in flight at dispatch: " + (($s2 | Group-Object in_flight_at_dispatch | Sort-Object { [int]$_.Name } | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' ')
	"triggers: " + (($s2 | Group-Object trigger | Sort-Object Count -Descending | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
	$long = @($m2 | Where-Object { $_.raw_response.Length -gt 600 })
	"long replies (>600 chars, runaway sign): $($long.Count)  max chars=$(($m2.raw_response | ForEach-Object { $_.Length } | Measure-Object -Maximum).Maximum)"
	$notes = @($m2.memory_notes | Select-Object -Unique)
	"memory notes: distinct=$($notes.Count) containing digits=$(@($notes | Where-Object { $_ -match '\d' }).Count)"
	"dispositions: " + (($m2 | Group-Object disposition | Sort-Object Count -Descending | Select-Object -First 6 | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', ')
}

if ($Mob) {
	""
	"=== Timeline for mob $Mob"
	$rows | Where-Object { $_.mob_uuid.StartsWith($Mob) } | Sort-Object dispatched_at_ms | ForEach-Object {
		"t={0,6}s {1,-6} {2,-13} hp={3,3}% {4}" -f [math]::Round(($_.dispatched_at_ms - $t0) / 1000, 1), $_.source, $_.action,
			[math]::Round(100 * $_.health / $_.max_health),
			$(if ($_.has_target) { "tdist=$([math]::Round($_.target_distance, 1)) thp=$([math]::Round(100 * $_.target_health / $_.target_max_health))%" } else { 'no target' })
	}
}
