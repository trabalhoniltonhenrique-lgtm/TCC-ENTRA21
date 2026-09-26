# CasaCapital - gera o pacote de instalacao para o cliente (rodar na maquina de desenvolvimento)
#
# Resultado: dist\CasaCapital-Instalador\ e dist\CasaCapital-Instalador.zip, contendo:
#   app\casacapital.jar   -> backend + frontend (o Maven copia ../frontend para /static)
#   app\runtime\          -> Java enxuto gerado com jlink (o cliente NAO precisa instalar Java)
#   app\CasaCapital.exe   -> WinSW, registra o jar como servico do Windows
#   Instalar.bat / Desinstalar.bat / configuracao.ini / LEIA-ME.txt
param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-26",
    [string]$WinSWUrl = "https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe"
)
$ErrorActionPreference = "Stop"

$raiz    = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $raiz "backend"
$dist    = Join-Path $raiz "dist"
$saida   = Join-Path $dist "CasaCapital-Instalador"
$app     = Join-Path $saida "app"
$cache   = Join-Path $PSScriptRoot ".cache"

Write-Host "== Gerando pacote de instalacao do CasaCapital ==" -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $JavaHome "bin\jlink.exe"))) {
    throw "JDK nao encontrado em $JavaHome (precisa de bin\jlink.exe). Use -JavaHome <caminho>."
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

# 1) Build do jar executavel
Write-Host "[1/4] Compilando o backend (mvnw package)..." -ForegroundColor Yellow
Push-Location $backend
try {
    & .\mvnw.cmd -q -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw "Falha no build do Maven (codigo $LASTEXITCODE)." }
} finally {
    Pop-Location
}
$jar = Get-ChildItem (Join-Path $backend "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) { throw "Jar nao encontrado em backend\target." }

if (Test-Path $saida) { Remove-Item -Recurse -Force $saida }
New-Item -ItemType Directory -Force -Path $app | Out-Null
Copy-Item $jar.FullName (Join-Path $app "casacapital.jar")

# 2) Runtime Java enxuto (so os modulos que o Spring Boot/MySQL/e-mail usam)
Write-Host "[2/4] Gerando runtime Java com jlink..." -ForegroundColor Yellow
$modulos = @(
    "java.se", "jdk.unsupported", "jdk.zipfs", "jdk.charsets", "jdk.localedata",
    "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.crypto.mscapi", "jdk.naming.dns",
    "jdk.management", "jdk.net"
) | Where-Object { Test-Path (Join-Path $JavaHome "jmods\$_.jmod") }
& jlink --add-modules ($modulos -join ",") `
        --strip-debug --no-header-files --no-man-pages `
        --output (Join-Path $app "runtime")
if ($LASTEXITCODE -ne 0) { throw "Falha no jlink (codigo $LASTEXITCODE)." }

# 3) WinSW (wrapper de servico) - baixado uma vez e reaproveitado do cache
Write-Host "[3/4] Preparando wrapper de servico (WinSW)..." -ForegroundColor Yellow
$winsw = Join-Path $cache "WinSW-x64.exe"
if (-not (Test-Path $winsw)) {
    New-Item -ItemType Directory -Force -Path $cache | Out-Null
    Write-Host "      Baixando $WinSWUrl"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $WinSWUrl -OutFile $winsw -UseBasicParsing
}
Copy-Item $winsw (Join-Path $app "CasaCapital.exe")

# 4) Scripts do instalador + zip final
Write-Host "[4/4] Montando pacote..." -ForegroundColor Yellow
Copy-Item (Join-Path $PSScriptRoot "pacote\*") $saida -Recurse -Force

$zip = Join-Path $dist "CasaCapital-Instalador.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Compress-Archive -Path $saida -DestinationPath $zip

$tamanho = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ""
Write-Host "Pacote pronto: $zip ($tamanho MB)" -ForegroundColor Green
Write-Host "Antes de enviar ao cliente, revise $saida\configuracao.ini (senhas do banco e e-mail)." -ForegroundColor Green
