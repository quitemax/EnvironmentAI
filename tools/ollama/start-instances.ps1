<#
.SYNOPSIS
  Starts two native Ollama instances for EnvironmentAI: one per AI tier.

.DESCRIPTION
  System 1 (fast, high-volume)  -> 127.0.0.1:11435, small model
  System 2 (slow, narrative)    -> 127.0.0.1:11436, larger model
  Port 11434 is deliberately left alone (it may be held by a WSL/Docker Ollama).
  Both instances share the default model store, so a model is only downloaded once.
  Memory is tuned per instance: the mod's prompts are a few hundred tokens, so a small context
  and low parallelism keep the KV cache (which scales with context x parallel slots) small.

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
	[int]$System2Port = 11436,
	# System 1 is the high-volume tier; System 2 fires rarely but writes longer output.
	[int]$System1Context = 2048,
	[int]$System2Context = 4096,
	[int]$System1Parallel = 2,
	[int]$System2Parallel = 1
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
			# /T: also kill the llama-server runner child, which otherwise outlives `ollama serve`
			# and keeps the model resident in VRAM.
			taskkill /T /F /PID $procId | Out-Null
			"Stopped instance on :$port (pid $procId)"
		}
	}
	return
}

$ollama = (Get-Command ollama).Source

function Start-Instance([int]$Port, [string]$Name, [int]$Context, [int]$Parallel) {
	if (Get-ListenerPid $Port) {
		"$Name already listening on :$Port (restart it with -Stop to apply new settings)"
		return
	}
	$env:OLLAMA_HOST = "127.0.0.1:$Port"
	$env:OLLAMA_CONTEXT_LENGTH = "$Context"
	$env:OLLAMA_NUM_PARALLEL = "$Parallel"
	try {
		Start-Process -FilePath $ollama -ArgumentList 'serve' -WindowStyle Hidden | Out-Null
	} finally {
		Remove-Item Env:OLLAMA_HOST, Env:OLLAMA_CONTEXT_LENGTH, Env:OLLAMA_NUM_PARALLEL
	}
	for ($i = 0; $i -lt 30; $i++) {
		if (Get-ListenerPid $Port) { "$Name started on :$Port (context $Context, parallel $Parallel)"; return }
		Start-Sleep -Milliseconds 500
	}
	throw "$Name did not start listening on :$Port"
}

# KEEP_ALIVE=-1 matters most: the default 5 minute unload would make the first call after a quiet
# spell exceed the mod's 10s timeout and silently fall back. Flash attention + a q8_0 KV cache
# roughly halve KV memory.
$env:OLLAMA_KEEP_ALIVE = '-1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_FLASH_ATTENTION = '1'
$env:OLLAMA_KV_CACHE_TYPE = 'q8_0'

Start-Instance $System1Port 'System 1' $System1Context $System1Parallel
Start-Instance $System2Port 'System 2' $System2Context $System2Parallel

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
