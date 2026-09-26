# CasaCapital - remove o servico do Windows.
# O banco de dados (MySQL) NAO e apagado: os dados dos clientes continuam la.
$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}

$NomeServico = "CasaCapital"
$destino     = Join-Path $env:ProgramFiles "CasaCapital"

try {
    $servico = Get-Service -Name $NomeServico -ErrorAction SilentlyContinue
    if ($servico) {
        Write-Host "Parando e removendo o servico..." -ForegroundColor Cyan
        if ($servico.Status -ne "Stopped") { Stop-Service -Name $NomeServico -Force }
        $winsw = Join-Path $destino "CasaCapital.exe"
        if (Test-Path $winsw) { & $winsw uninstall | Out-Null } else { sc.exe delete $NomeServico | Out-Null }
        Start-Sleep -Seconds 2
    } else {
        Write-Host "Servico nao estava instalado." -ForegroundColor Yellow
    }

    foreach ($pasta in @([Environment]::GetFolderPath("CommonDesktopDirectory"), [Environment]::GetFolderPath("CommonPrograms"))) {
        if ($pasta) { Remove-Item (Join-Path $pasta "CasaCapital.url") -ErrorAction SilentlyContinue }
    }

    # Se este script estiver rodando de dentro da pasta instalada, sai dela antes de apagar
    Set-Location $env:TEMP
    if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }

    Write-Host ""
    Write-Host "CasaCapital removido." -ForegroundColor Green
    Write-Host "Logs e chave de sessao ficaram em $env:ProgramData\CasaCapital (pode apagar se quiser)." -ForegroundColor Gray
    Write-Host "O banco de dados 'casacapital' no MySQL foi mantido." -ForegroundColor Gray
} catch {
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
Read-Host "Pressione Enter para fechar" | Out-Null
