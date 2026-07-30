@echo off
REM DrainQ.ONE - Werkseinrichtung, Bestandsgeraet-Modus (CEO-Entscheid 30.07.2026).
REM NUR verwenden, wenn ausdruecklich so entschieden: Geraet mit bereits installierter
REM DrainQ.ONE-App (z.B. alte Signatur), auf dem NICHTS schuetzenswert ist. Entfernt die
REM vorhandene App (Rueckfrage folgt), KEIN Werksreset. Sonst bitte Start-Werkseinrichtung.cmd.
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Werkseinrichtung.ps1" -Bestandsgeraet
