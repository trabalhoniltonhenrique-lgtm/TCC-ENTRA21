# CasaCapital - restaura o banco de dados a partir de um backup diario.
# Atalho: Menu Iniciar > "CasaCapital - Restaurar backup" (pede permissao de administrador).
# ATENCAO: substitui TODOS os dados do sistema pelos do backup escolhido. Antes de restaurar, faz um backup do
# estado atual, para dar para voltar atras.
$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}

$NomeServico = "CasaCapital"
$dados = Join-Path $env:ProgramData "CasaCapital"
$pasta = Join-Path $dados "backups"
$cnf   = Join-Path $pasta "mysql.cnf"

function Sair($codigo) { Write-Host ""; Read-Host "Pressione Enter para fechar" | Out-Null; exit $codigo }

function MySql($argumentos, $entrada) {
    $exe = (Get-ChildItem "$env:ProgramFiles\MySQL\*\bin\mysql.exe" | Sort-Object FullName -Descending | Select-Object -First 1).FullName
    $todos = @("--defaults-extra-file=`"$cnf`"") + $argumentos
    $p = if ($entrada) {
        Start-Process -FilePath $exe -ArgumentList $todos -RedirectStandardInput $entrada -NoNewWindow -Wait -PassThru
    } else {
        Start-Process -FilePath $exe -ArgumentList $todos -NoNewWindow -Wait -PassThru
    }
    if ($p.ExitCode -ne 0) { throw "mysql.exe terminou com codigo $($p.ExitCode)." }
}

try {
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host "   CasaCapital - Restaurar backup" -ForegroundColor Cyan
    Write-Host "==============================================" -ForegroundColor Cyan
    if (-not (Test-Path $cnf)) { throw "Credenciais do backup nao encontradas ($cnf). Rode o Instalar.bat de novo." }

    $backups = @(Get-ChildItem $pasta -Filter "banco-casacapital-*.sql" | Sort-Object Name -Descending)
    if (-not $backups) { throw "Nenhum backup encontrado em $pasta." }

    Write-Host ""
    Write-Host "Backups disponiveis (mais recente primeiro):"
    for ($i = 0; $i -lt $backups.Count; $i++) {
        $b = $backups[$i]
        Write-Host ("  {0,2}) {1:dd/MM/yyyy HH:mm}   {2,6} KB   {3}" -f ($i + 1), $b.LastWriteTime, [math]::Round($b.Length / 1KB), $b.Name)
    }
    Write-Host ""
    $escolha = Read-Host "Numero do backup a restaurar (Enter para cancelar)"
    if (-not $escolha) { Write-Host "Cancelado."; Sair 0 }
    $n = 0
    if (-not [int]::TryParse($escolha, [ref]$n) -or $n -lt 1 -or $n -gt $backups.Count) { throw "Numero invalido." }
    $alvo = $backups[$n - 1]

    Write-Host ""
    Write-Host "Isto vai SUBSTITUIR todos os dados atuais do CasaCapital pelos de $($alvo.LastWriteTime.ToString('dd/MM/yyyy HH:mm'))." -ForegroundColor Yellow
    Write-Host "Tudo o que foi lancado depois dessa data sera perdido (fica guardado no backup de seguranca abaixo)." -ForegroundColor Yellow
    if ((Read-Host "Digite RESTAURAR para confirmar") -ne "RESTAURAR") { Write-Host "Cancelado."; Sair 0 }

    Write-Host ""
    Write-Host "> Fazendo backup de seguranca do estado atual..." -ForegroundColor Cyan
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "backup.ps1") -Manter 60
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel fazer o backup de seguranca; nada foi alterado. Veja $dados\logs\backup.log." }
    $seguranca = (Get-ChildItem $pasta -Filter "banco-casacapital-*.sql" | Sort-Object Name -Descending | Select-Object -First 1).Name
    Write-Host "  Backup de seguranca: $seguranca" -ForegroundColor Green

    Write-Host "> Parando o sistema..." -ForegroundColor Cyan
    Stop-Service -Name $NomeServico -Force -ErrorAction SilentlyContinue

    Write-Host "> Restaurando $($alvo.Name)..." -ForegroundColor Cyan
    # Apaga as tabelas atuais primeiro: tabelas criadas depois do backup (versoes novas) nao podem sobrar
    $lista = Join-Path $env:TEMP "casacapital-tabelas.sql"
    $exe = (Get-ChildItem "$env:ProgramFiles\MySQL\*\bin\mysql.exe" | Sort-Object FullName -Descending | Select-Object -First 1).FullName
    $tabelas = & $exe "--defaults-extra-file=$cnf" -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='casacapital'"
    $drop = "SET FOREIGN_KEY_CHECKS=0;`n" + (($tabelas | Where-Object { $_ } | ForEach-Object { "DROP TABLE IF EXISTS ``$_``;" }) -join "`n") + "`nSET FOREIGN_KEY_CHECKS=1;`n"
    [IO.File]::WriteAllText($lista, $drop)
    MySql @("casacapital") $lista
    Remove-Item $lista -ErrorAction SilentlyContinue
    MySql @("casacapital") $alvo.FullName

    Write-Host "> Iniciando o sistema..." -ForegroundColor Cyan
    Start-Service -Name $NomeServico
    Write-Host ""
    Write-Host "Backup restaurado com sucesso." -ForegroundColor Green
    Write-Host "Se precisar desfazer, rode este atalho de novo e escolha $seguranca." -ForegroundColor Gray
    Sair 0
} catch {
    Write-Host ""
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    if ($seguranca) { Write-Host "Os dados de antes da restauracao estao em $pasta\$seguranca." -ForegroundColor Yellow }
    Start-Service -Name $NomeServico -ErrorAction SilentlyContinue
    Sair 1
}
