<#
.SYNOPSIS
  Switch which model each EnvironmentAI tier uses: restarts the Ollama instances and rewrites the
  tiers in config/environmentai.json to match. Restart the game afterwards (config is read at startup).

.DESCRIPTION
  Run with no arguments to see the current setup and the installed models.

  A model whose name starts with "claude" is a cloud model: that tier is set to CLOUD and gets no
  Ollama instance (needs ANTHROPIC_API_KEY). Anything else is a local Ollama model.
  If both tiers use the same local model, one shared instance is started (see start-instances.ps1).
  System 3 (villager dialogue) follows System 2 unless -System3Model is given.

.EXAMPLE
  .\use-models.ps1 -System1 qwen3.5:4b                    # try a new System 1, keep System 2
  .\use-models.ps1 -System1 llama3.2:3b -System2 qwen3.5:4b
  .\use-models.ps1 -System2 claude-sonnet-5-5              # System 2 on the cloud
  .\use-models.ps1 -Profile fast                           # a named preset from profiles.json
  .\use-models.ps1 -Stop
#>
param(
	[string]$System1,
	[string]$System2,
	[string]$System3,
	[string]$Profile,
	[switch]$Pull,
	[switch]$NoWarm,
	[switch]$Stop,
	[string]$Config = (Join-Path $PSScriptRoot '..\..\run\config\environmentai.json')
)

$ErrorActionPreference = 'Stop'
$starter = Join-Path $PSScriptRoot 'start-instances.ps1'
$cfg = Get-Content $Config -Raw | ConvertFrom-Json

if ($Stop) { & $starter -Stop; return }

if ($Profile) {
	$profiles = Get-Content (Join-Path $PSScriptRoot 'profiles.json') -Raw | ConvertFrom-Json
	$p = $profiles.$Profile
	if (-not $p) { throw "No profile '$Profile'. Known: $($profiles.PSObject.Properties.Name -join ', ')" }
	if (-not $System1) { $System1 = $p.system1 }
	if (-not $System2) { $System2 = $p.system2 }
	if (-not $System3) { $System3 = $p.system3 }
}

if (-not $System1 -and -not $System2 -and -not $System3) {
	"Current config ($Config):"
	foreach ($t in 'system1', 'system2', 'system3') {
		"  {0}: {1} {2}  {3}" -f $t, $cfg.$t.provider, $cfg.$t.model, $cfg.$t.endpointOverride
	}
	""
	"Installed models:"
	ollama list
	""
	"Usage: use-models.ps1 -System1 <model> [-System2 <model>] [-System3 <model>] [-Profile <name>] [-Pull]"
	return
}

# Unspecified tiers keep what the config has now.
if (-not $System1) { $System1 = $cfg.system1.model }
if (-not $System2) { $System2 = $cfg.system2.model }
if (-not $System3) { $System3 = $System2 }

function Test-Cloud([string]$m) { $m -like 'claude*' }

$s1Cloud = Test-Cloud $System1
$s2Cloud = Test-Cloud $System2
$s3Cloud = Test-Cloud $System3
$local = @($System1, $System2, $System3) | Where-Object { -not (Test-Cloud $_) } | Select-Object -Unique
if ($local.Count -gt 2) { throw "At most two distinct local models can be resident at once; got: $($local -join ', ')" }

# Same local model everywhere (or only one tier local) -> one shared instance; otherwise one per
# System 1/System 2, with System 3 riding on System 2's instance (same model by default).
$shared = $local.Count -le 1
$port1 = 11435
$port2 = if ($shared) { 11435 } else { 11436 }
if (-not $shared -and -not $s3Cloud -and $System3 -ne $System1 -and $System3 -ne $System2) {
	throw "System 3 ($System3) must match System 1 or System 2 when they differ."
}
$port3 = if ($shared -or $System3 -ne $System1) { $port2 } else { $port1 }

& $starter -Stop
if ($local.Count -gt 0) {
	$a = @{ System1Model = $(if ($s1Cloud) { $local[0] } else { $System1 })
	        System2Model = $(if ($s2Cloud) { $local[0] } else { $System2 }) }
	if ($shared) { $a.Shared = $true }
	if ($Pull) { $a.Pull = $true }
	if (-not $NoWarm) { $a.Warm = $true }
	& $starter @a
}

function Set-Tier($tier, [string]$model, [int]$port) {
	if (Test-Cloud $model) {
		$tier.provider = 'CLOUD'
		$tier.model = $model
		$tier.endpointOverride = $null
	} else {
		$tier.provider = 'LOCAL'
		$tier.model = $model
		$tier.endpointOverride = "http://127.0.0.1:$port"
	}
	# Reasoning models think by default and spend the whole token budget doing it.
	$think = if ($model -match 'qwen|deepseek-r1') { $false } else { $null }
	$tier | Add-Member -NotePropertyName think -NotePropertyValue $think -Force
}
Set-Tier $cfg.system1 $System1 $port1
Set-Tier $cfg.system2 $System2 $port2
Set-Tier $cfg.system3 $System3 $port3

$cfg | ConvertTo-Json -Depth 10 | Set-Content $Config -Encoding utf8
""
"Config updated:"
foreach ($t in 'system1', 'system2', 'system3') {
	"  {0}: {1} {2}  {3}" -f $t, $cfg.$t.provider, $cfg.$t.model, $cfg.$t.endpointOverride
}
"Restart the game to pick it up."
