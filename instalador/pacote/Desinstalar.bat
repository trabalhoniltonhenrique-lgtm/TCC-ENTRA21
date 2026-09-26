@echo off
rem CasaCapital - remove o servico (pede permissao de administrador)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\desinstalar.ps1"
