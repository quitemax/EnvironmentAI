<#
.SYNOPSIS
  Copies the EnvironmentAI debug datapack into a dev-run world's datapacks folder.
.EXAMPLE
  .\tools\install-datapack.ps1 -World test
#>
param(
	[Parameter(Mandatory)][string]$World
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'testing\datapack\environmentai_debug'
$worldDir = Join-Path $root "run\saves\$World"
if (-not (Test-Path $worldDir)) { throw "World not found: $worldDir" }

$target = Join-Path $worldDir 'datapacks\environmentai_debug'
if (Test-Path $target) { Remove-Item -Recurse -Force $target }
New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
Copy-Item -Recurse $source $target
"Installed to $target. In-game: /reload, then /function eai:setup and /function eai:help"
