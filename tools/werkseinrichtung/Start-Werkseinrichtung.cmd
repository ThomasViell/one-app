@echo off
REM DrainQ.ONE - Werkseinrichtung. Einfach doppelklicken.
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Werkseinrichtung.ps1"
