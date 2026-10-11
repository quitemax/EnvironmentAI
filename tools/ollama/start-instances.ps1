<#
.SYNOPSIS
  Starts two native Ollama instances for EnvironmentAI: one per AI tier.

.DESCRIPTION
  System 1 (fast, high-volume)  -> 127.0.0.1:11435, small model
  System 2 (slow, narrative)    -> 127.0.0.1:11436, larger model
  Port 11434 is deliberately left alone (it may be held by a WSL/Docker Ollama).
  Both instances share the default model store, so a model is only downloaded once.

.PARAMETER Pull
  Also pull the configured models through their instances.

.PARAMETER Stop
  Stop the instances started by this script (matched by listening port) and exit.
#>
param(
	[switch]$Pull,
	[switch]$Stop,
	[string]$System1Model = 'llama3.2:3b',
	[string]$System2Model = 'llama3.1:8b',
	[int]$System1Port = 11435,
	[int]$System2Port = 11436
)

$ErrorActionPreference = 'Stop'

function Get-ListenerPid([int]$Port) {
	(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
		Select-Object -First 1).OwningProcess
}

if ($Stop) {
	foreach ($port in $System1Port, $System2Port) {
		$procId = Get-ListenerPid $port
		if ($procId) {
			Stop-Process -Id $procId -Force
			"Stopped instance on :$port (pid $procId)"
		}
	}
	return
}

$ollama = (Get-Command ollama).Source

function Start-Instance([int]$Port, [string]$Name) {
	if (Get-ListenerPid $Port) {
		"$Name already listening on :$Port"
		return
	}
	$env:OLLAMA_HOST = "127.0.0.1:$Port"
	try {
		Start-Process -FilePath $ollama -ArgumentList 'serve' -WindowStyle Hidden | Out-Null
	} finally {
		Remove-Item Env:OLLAMA_HOST
	}
	for ($i = 0; $i -lt 30; $i++) {
		if (Get-ListenerPid $Port) { "$Name started on :$Port"; return }
		Start-Sleep -Milliseconds 500
	}
	throw "$Name did not start listening on :$Port"
}

# Per-instance tuning. KEEP_ALIVE=-1 matters most: the default 5 minute unload would make the
# first call after a quiet spell exceed the mod's 10s timeout and silently fall back.
$env:OLLAMA_KEEP_ALIVE = '-1'
$env:OLLAMA_NUM_PARALLEL = '4'
$env:OLLAMA_MAX_LOADED_MODELS = '1'

Start-Instance $System1Port 'System 1'
Start-Instance $System2Port 'System 2'

if ($Pull) {
	# Pull over the REST API rather than the CLI: the CLI's progress output goes to stderr, which
	# Windows PowerShell 5.1 turns into terminating errors under $ErrorActionPreference = 'Stop'.
	foreach ($pair in @(@($System1Port, $System1Model), @($System2Port, $System2Model))) {
		"Pulling $($pair[1]) via :$($pair[0]) ..."
		$body = @{ model = $pair[1]; stream = $false } | ConvertTo-Json
		$result = Invoke-RestMethod -Uri "http://127.0.0.1:$($pair[0])/api/pull" -Method Post `
			-Body $body -ContentType 'application/json' -TimeoutSec 3600
		"  $($pair[1]): $($result.status)"
	}
}
