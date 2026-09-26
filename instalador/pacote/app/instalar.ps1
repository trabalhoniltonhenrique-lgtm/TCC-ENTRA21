# CasaCapital - instala (ou atualiza) o sistema como servico do Windows.
# Depois de instalado, o sistema sobe sozinho junto com o Windows; o cliente
# so precisa abrir http://localhost:PORTA no navegador (atalho na Area de Trabalho).
$ErrorActionPreference = "Stop"

# --- Eleva para administrador se necessario --------------------------------
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}

$NomeServico = "CasaCapital"
$origemApp   = $PSScriptRoot
$origem      = Split-Path $origemApp -Parent
$destino     = Join-Path $env:ProgramFiles "CasaCapital"
$dados       = Join-Path $env:ProgramData "CasaCapital"

function Passo($texto) { Write-Host ""; Write-Host "> $texto" -ForegroundColor Cyan }
function Sair($codigo) { Write-Host ""; Read-Host "Pressione Enter para fechar" | Out-Null; exit $codigo }

function Ler-Configuracao($arquivo) {
    $cfg = @{}
    foreach ($linha in Get-Content $arquivo) {
        $l = $linha.Trim()
        if ($l -eq "" -or $l.StartsWith("#")) { continue }
        $i = $l.IndexOf("=")
        if ($i -gt 0) { $cfg[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim() }
    }
    return $cfg
}

function Encontrar-MySqlExe {
    $candidatos = Get-ChildItem "$env:ProgramFiles\MySQL\*\bin\mysql.exe" -ErrorAction SilentlyContinue
    if ($candidatos) { return ($candidatos | Sort-Object FullName -Descending | Select-Object -First 1).FullName }
    $cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Testar-Login($mysqlExe, $usuario, $senha, $banco) {
    $ErrorActionPreference = "Continue"   # erro do mysql.exe no stderr nao deve abortar o script
    $env:MYSQL_PWD = $senha
    try {
        $argumentos = @("-u", $usuario, "-h", "localhost", "-e", "SELECT 1")
        if ($banco) { $argumentos += $banco }
        & $mysqlExe @argumentos 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

try {
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host "   Instalador do servico CasaCapital" -ForegroundColor Cyan
    Write-Host "==============================================" -ForegroundColor Cyan

    # --- 0) Pacote completo? ------------------------------------------------
    $faltando = @("runtime\bin\java.exe", "casacapital.jar", "CasaCapital.exe") |
        Where-Object { -not (Test-Path (Join-Path $origemApp $_)) }
    if ($faltando) {
        throw ("Pacote incompleto (faltando: " + ($faltando -join ", ") + ").`n" +
               "  Esta pasta e so o modelo do instalador. Gere o pacote com instalador\gerar-pacote.ps1`n" +
               "  e rode o Instalar.bat de dentro de dist\CasaCapital-Instalador.")
    }

    # --- 1) Configuracao ----------------------------------------------------
    Passo "Lendo configuracao.ini"
    $cfg = Ler-Configuracao (Join-Path $origem "configuracao.ini")
    $porta        = if ($cfg["PORTA"]) { $cfg["PORTA"] } else { "8080" }
    $servicoMysql = if ($cfg["SERVICO_MYSQL"]) { $cfg["SERVICO_MYSQL"] } else { "MySQL80" }
    $dbUser       = $cfg["DB_USERNAME"]
    $dbPass       = $cfg["DB_PASSWORD"]
    if (-not $dbUser -or -not $dbPass) { throw "Preencha DB_USERNAME e DB_PASSWORD no configuracao.ini." }
    $url = "http://localhost:$porta"

    # --- 2) MySQL -----------------------------------------------------------
    Passo "Verificando MySQL (servico '$servicoMysql')"
    $mysql = Get-Service -Name $servicoMysql -ErrorAction SilentlyContinue
    if (-not $mysql) {
        throw "Servico '$servicoMysql' nao encontrado. Instale o MySQL Server 8 (ou ajuste SERVICO_MYSQL no configuracao.ini)."
    }
    Set-Service -Name $servicoMysql -StartupType Automatic
    if ($mysql.Status -ne "Running") { Start-Service -Name $servicoMysql }
    Write-Host "  MySQL rodando e configurado para iniciar com o Windows." -ForegroundColor Green

    $mysqlExe = Encontrar-MySqlExe
    if (-not $mysqlExe) {
        Write-Host "  AVISO: mysql.exe nao encontrado; nao da para conferir o banco. Garanta que o banco 'casacapital' e o usuario '$dbUser' existem." -ForegroundColor Yellow
    } elseif (Testar-Login $mysqlExe $dbUser $dbPass "casacapital") {
        Write-Host "  Banco 'casacapital' e usuario '$dbUser' OK." -ForegroundColor Green
    } else {
        Write-Host "  Banco/usuario do CasaCapital ainda nao existem (ou a senha mudou). Vamos criar." -ForegroundColor Yellow
        $rootSeguro = Read-Host "  Senha do usuario root do MySQL" -AsSecureString
        $rootSenha = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootSeguro))
        $senhaSql = $dbPass.Replace("\", "\\").Replace("'", "''")
        $sql = @"
CREATE DATABASE IF NOT EXISTS casacapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$dbUser'@'localhost' IDENTIFIED BY '$senhaSql';
ALTER USER '$dbUser'@'localhost' IDENTIFIED BY '$senhaSql';
GRANT ALL PRIVILEGES ON casacapital.* TO '$dbUser'@'localhost';
FLUSH PRIVILEGES;
"@
        $env:MYSQL_PWD = $rootSenha
        $ErrorActionPreference = "Continue"
        try {
            $sql | & $mysqlExe -u root -h localhost
            $codigo = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = "Stop"
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        }
        if ($codigo -ne 0) { throw "Falha ao criar o banco (senha do root correta?)." }
        Write-Host "  Banco e usuario criados." -ForegroundColor Green
    }

    # --- 3) Para servico antigo (atualizacao) ------------------------------
    $winswDestino = Join-Path $destino "CasaCapital.exe"
    $existente = Get-Service -Name $NomeServico -ErrorAction SilentlyContinue
    if ($existente) {
        Passo "Servico ja instalado - atualizando"
        if ($existente.Status -ne "Stopped") { Stop-Service -Name $NomeServico -Force }
        if (Test-Path $winswDestino) { & $winswDestino uninstall | Out-Null } else { sc.exe delete $NomeServico | Out-Null }
        Start-Sleep -Seconds 2
    }

    # --- 4) Copia arquivos --------------------------------------------------
    Passo "Copiando arquivos para $destino"
    New-Item -ItemType Directory -Force -Path $destino | Out-Null
    if (Test-Path (Join-Path $destino "runtime")) { Remove-Item -Recurse -Force (Join-Path $destino "runtime") }
    Copy-Item (Join-Path $origemApp "runtime") $destino -Recurse -Force
    Copy-Item (Join-Path $origemApp "casacapital.jar") $destino -Force
    Copy-Item (Join-Path $origemApp "CasaCapital.exe") $destino -Force
    Copy-Item (Join-Path $origemApp "desinstalar.ps1") $destino -Force

    foreach ($pasta in @($dados, "$dados\logs", "$dados\tmp")) {
        New-Item -ItemType Directory -Force -Path $pasta | Out-Null
    }

    # Segredo do JWT: gerado uma vez e mantido entre atualizacoes (senao todos
    # os usuarios seriam deslogados a cada reinstalacao).
    $arquivoJwt = Join-Path $dados "jwt.secret"
    if (-not (Test-Path $arquivoJwt)) {
        $bytes = New-Object byte[] 48
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        Set-Content -Path $arquivoJwt -Value ([Convert]::ToBase64String($bytes)) -NoNewline -Encoding ASCII
    }
    $jwt = (Get-Content $arquivoJwt -Raw).Trim()

    # --- 5) Configuracao do servico (WinSW) --------------------------------
    Passo "Configurando servico"
    function X($v) { [Security.SecurityElement]::Escape([string]$v) }
    $xml = @"
<service>
  <id>$NomeServico</id>
  <name>CasaCapital</name>
  <description>Sistema CasaCapital - acesse $url no navegador.</description>
  <executable>%BASE%\runtime\bin\java.exe</executable>
  <arguments>-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir="$(X "$dados\tmp")" -jar "%BASE%\casacapital.jar"</arguments>
  <workingdirectory>$(X $dados)</workingdirectory>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
  <depend>$(X $servicoMysql)</depend>
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <resetfailure>1 hour</resetfailure>
  <stoptimeout>30 sec</stoptimeout>
  <logpath>$(X "$dados\logs")</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>5</keepFiles>
  </log>
  <env name="SERVER_PORT" value="$(X $porta)"/>
  <env name="FRONTEND_BASE_URL" value="$(X $url)"/>
  <env name="DB_USERNAME" value="$(X $dbUser)"/>
  <env name="DB_PASSWORD" value="$(X $dbPass)"/>
  <env name="MAIL_USERNAME" value="$(X $cfg["MAIL_USERNAME"])"/>
  <env name="MAIL_PASSWORD" value="$(X $cfg["MAIL_PASSWORD"])"/>
  <env name="JWT_SECRET" value="$(X $jwt)"/>
</service>
"@
    $xmlArquivo = Join-Path $destino "CasaCapital.xml"
    [IO.File]::WriteAllText($xmlArquivo, $xml, (New-Object Text.UTF8Encoding($false)))

    # O XML e o jwt.secret tem senhas: so Administradores e SYSTEM podem ler
    foreach ($arquivo in @($xmlArquivo, $arquivoJwt)) {
        icacls $arquivo /inheritance:r /grant:r "*S-1-5-32-544:F" "*S-1-5-18:F" | Out-Null
    }

    # --- 6) Instala e inicia ------------------------------------------------
    Passo "Instalando e iniciando o servico"
    & $winswDestino install
    if ($LASTEXITCODE -ne 0) { throw "Falha ao instalar o servico (codigo $LASTEXITCODE)." }
    & $winswDestino start
    if ($LASTEXITCODE -ne 0) { throw "Falha ao iniciar o servico (codigo $LASTEXITCODE)." }

    Write-Host "  Aguardando o sistema responder em $url ..." -NoNewline
    $ok = $false
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        try {
            $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -lt 500) { $ok = $true; break }
        } catch {
            if ($_.Exception.Response) { $ok = $true; break }
        }
        if ((Get-Service -Name $NomeServico).Status -eq "Stopped") { break }
        Write-Host "." -NoNewline
    }
    Write-Host ""
    if (-not $ok) {
        throw "O servico nao respondeu. Veja os logs em $dados\logs (casacapital.log e CasaCapital.err.log)."
    }

    # --- 7) Atalhos ---------------------------------------------------------
    Passo "Criando atalhos"
    $conteudoAtalho = "[InternetShortcut]`r`nURL=$url`r`n"
    $areaTrabalho = [Environment]::GetFolderPath("CommonDesktopDirectory")
    $menuIniciar  = [Environment]::GetFolderPath("CommonPrograms")
    foreach ($pasta in @($areaTrabalho, $menuIniciar)) {
        if ($pasta) { Set-Content -Path (Join-Path $pasta "CasaCapital.url") -Value $conteudoAtalho -Encoding ASCII }
    }

    Write-Host ""
    Write-Host "==============================================" -ForegroundColor Green
    Write-Host " CasaCapital instalado com sucesso!" -ForegroundColor Green
    Write-Host " Acesse: $url  (atalho na Area de Trabalho)" -ForegroundColor Green
    Write-Host " O sistema inicia sozinho junto com o Windows." -ForegroundColor Green
    Write-Host "==============================================" -ForegroundColor Green
    Start-Process $url
    Sair 0
} catch {
    Write-Host ""
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    Sair 1
}
