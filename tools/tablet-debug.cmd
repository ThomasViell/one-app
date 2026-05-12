@echo off
REM Wrapper: erlaubt Doppelklick-Start ohne PowerShell-ExecutionPolicy-Aerger
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tablet-debug.ps1" %*
pause
