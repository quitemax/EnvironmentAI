@echo off
rem Double-click to see the current model setup and switch it. Copy or shortcut this to the desktop.
rem Or run from a terminal:  EnvironmentAI-models.bat -System1 qwen3.5:4b -System2 llama3.2:3b
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0use-models.ps1" %*
echo.
if "%~1"=="" (
	echo Examples:
	echo   EnvironmentAI-models.bat -Profile qwen
	echo   EnvironmentAI-models.bat -System1 qwen3.5:4b -Pull
	echo   EnvironmentAI-models.bat -Stop
)
pause
