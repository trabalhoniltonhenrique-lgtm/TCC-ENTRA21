@echo off
rem CasaCapital - instala/atualiza o servico (pede permissao de administrador)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\instalar.ps1"
