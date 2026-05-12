@echo off
title DrainQ Tablet Watcher
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tablet-watcher.ps1" %*
pause
