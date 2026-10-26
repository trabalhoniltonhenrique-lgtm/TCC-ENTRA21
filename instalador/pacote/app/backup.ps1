# CasaCapital - backup do banco de dados (roda todo dia pela tarefa agendada "CasaCapital - Backup diario").
# Gera C:\ProgramData\CasaCapital\backups\banco-casacapital-AAAA-MM-DD_HHmm.sql e mantem os 30 mais recentes.
# As credenciais ficam em backups\mysql.cnf, que so Administradores e SYSTEM conseguem ler.
param(
    [int]$Manter = 30
)
$ErrorActionPreference = "Stop"

$dados  = Join-Path $env:ProgramData "CasaCapital"
$pasta  = Join-Path $dados "backups"
$cnf    = Join-Path $pasta "mysql.cnf"
$status = Join-Path $pasta "ultimo-backup.json"
$log    = Join-Path $dados "logs\backup.log"

function Registrar($texto) {
    $linha = "{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $texto
    Add-Content -Path $log -Value $linha -Encoding UTF8
}

function Gravar-Status($ok, $arquivo, $mensagem) {
    $obj = [ordered]@{
        ok       = $ok
        data     = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
        arquivo  = $arquivo
        mensagem = $mensagem
    }
    [IO.File]::WriteAllText($status, ($obj | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
}

try {
    New-Item -ItemType Directory -Force -Path $pasta, (Split-Path $log) | Out-Null
    if (-not (Test-Path $cnf)) { throw "Arquivo de credenciais $cnf nao encontrado. Rode o Instalar.bat de novo." }

    $dump = Get-ChildItem "$env:ProgramFiles\MySQL\*\bin\mysqldump.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $dump) { throw "mysqldump.exe nao encontrado em $env:ProgramFiles\MySQL." }

    $arquivo = Join-Path $pasta ("banco-casacapital-{0:yyyy-MM-dd_HHmm}.sql" -f (Get-Date))
    $parcial = "$arquivo.parcial"
    $ErrorActionPreference = "Continue"   # avisos do mysqldump no stderr nao devem abortar
    & $dump.FullName "--defaults-extra-file=$cnf" --no-tablespaces --single-transaction --routines `
        "--result-file=$parcial" casacapital 2>&1 | ForEach-Object { Registrar "  mysqldump: $_" }
    $codigo = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($codigo -ne 0 -or -not (Test-Path $parcial)) { throw "mysqldump terminou com codigo $codigo." }
    # So vira backup "de verdade" se o dump terminou (a ultima linha do mysqldump confirma)
    $fim = Get-Content $parcial -Tail 1
    if ($fim -notmatch "Dump completed") { throw "O arquivo gerado esta incompleto." }
    Move-Item -Force $parcial $arquivo

    # Mantem so os mais recentes
    Get-ChildItem $pasta -Filter "banco-casacapital-*.sql" | Sort-Object Name -Descending |
        Select-Object -Skip $Manter | Remove-Item -Force

    $tamanho = [math]::Round((Get-Item $arquivo).Length / 1KB)
    Registrar "OK  $([IO.Path]::GetFileName($arquivo)) ($tamanho KB)"
    Gravar-Status $true ([IO.Path]::GetFileName($arquivo)) "Backup concluido ($tamanho KB)."
    exit 0
} catch {
    if ($parcial -and (Test-Path $parcial)) { Remove-Item -Force $parcial -ErrorAction SilentlyContinue }
    Registrar "ERRO  $($_.Exception.Message)"
    try { Gravar-Status $false $null $_.Exception.Message } catch {}
    exit 1
}
