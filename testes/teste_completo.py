"""CasaCapital — teste completo num ambiente separado, sem tocar no sistema nem no banco da casa.

Cria um MySQL temporário (porta 3307, numa pasta temporária), sobe o sistema na porta 8090 apontando para ele,
roda os testes e, no fim (mesmo se algo falhar), desliga tudo e apaga a pasta temporária.

Uso (na pasta do projeto):
    python testes/teste_completo.py
        banco vazio: só o roteiro de 230 verificações (teste_api.py)
    python testes/teste_completo.py --banco C:\\Git\\casacapital-backups\\AAAA-MM-DD_HHMM\\banco-casacapital.sql
        cópia do banco da casa a partir de um backup: também abre todas as listas e relatórios de cada login com os
        dados reais (teste_dados_reais.py). A cópia contém dados reais e é apagada ao final.
    --jar CAMINHO   jar a testar (padrão: o do último pacote, dist/CasaCapital-Instalador/app/casacapital.jar)
    --manter        não desliga no final (para testar as telas no navegador em http://localhost:8090);
                    depois rode de novo com --limpar para desligar e apagar
"""
import argparse, os, shutil, socket, subprocess, sys, tempfile, time, urllib.request

sys.stdout.reconfigure(encoding='utf-8', errors='replace', line_buffering=True)  # console do Windows (cp1252)
RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MYSQL_BIN = r'C:\Program Files\MySQL\MySQL Server 8.0\bin'
JAVA = r'C:\Program Files\Java\jdk-26\bin\java.exe'
PORTA_BANCO, PORTA_APP = 3307, 8090
# Usados só no ambiente de teste (o teste_dados_reais.py assina os logins com o mesmo segredo)
SEGREDO_JWT = 'segredo-so-para-o-teste-local-0123456789abcdefghij'
USUARIO_BANCO, SENHA_BANCO = 'teste', 'teste-local-123'
PASTA_ESTADO = os.path.join(tempfile.gettempdir(), 'casacapital-teste-completo')


def porta_livre(porta):
    with socket.socket() as s:
        return s.connect_ex(('127.0.0.1', porta)) != 0


def mysql(*args, entrada=None):
    return subprocess.run([os.path.join(MYSQL_BIN, 'mysql.exe'), '-u', 'root', '-h', '127.0.0.1', '-P', str(PORTA_BANCO),
                           '--default-character-set=utf8mb4', *args], input=entrada, capture_output=True, check=True)


def esperar(condicao, segundos, oque):
    limite = time.time() + segundos
    while time.time() < limite:
        if condicao():
            return
        time.sleep(1)
    raise RuntimeError(f'{oque} não ficou pronto em {segundos} s')


def subir(jar, banco):
    for porta in (PORTA_BANCO, PORTA_APP):
        if not porta_livre(porta):
            raise RuntimeError(f'A porta {porta} já está em uso (outro teste ligado? rode com --limpar).')
    os.makedirs(PASTA_ESTADO, exist_ok=True)
    dados = os.path.join(PASTA_ESTADO, 'dados')
    ini = os.path.join(PASTA_ESTADO, 'my.ini')
    with open(ini, 'w', encoding='utf-8') as f:
        f.write(f'[mysqld]\nbasedir="{os.path.dirname(MYSQL_BIN)}"\ndatadir="{dados}"\nport={PORTA_BANCO}\n'
                f'mysqlx=OFF\nbind-address=127.0.0.1\nnamed_pipe=OFF\nshared_memory=OFF\n'
                f'log-error="{os.path.join(PASTA_ESTADO, "mysql-erro.log")}"\n'.replace('\\', '/'))
    print('• criando o MySQL temporário (porta 3307)...')
    subprocess.run([os.path.join(MYSQL_BIN, 'mysqld.exe'), f'--defaults-file={ini}', '--initialize-insecure'],
                   check=True, capture_output=True)
    subprocess.Popen([os.path.join(MYSQL_BIN, 'mysqld.exe'), f'--defaults-file={ini}'],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    esperar(lambda: not porta_livre(PORTA_BANCO), 60, 'O MySQL temporário')
    mysql('-e', f"CREATE DATABASE casacapital CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; "
                f"CREATE USER '{USUARIO_BANCO}'@'localhost' IDENTIFIED BY '{SENHA_BANCO}'; "
                f"CREATE USER '{USUARIO_BANCO}'@'127.0.0.1' IDENTIFIED BY '{SENHA_BANCO}'; "
                f"GRANT ALL ON casacapital.* TO '{USUARIO_BANCO}'@'localhost'; "
                f"GRANT ALL ON casacapital.* TO '{USUARIO_BANCO}'@'127.0.0.1';")
    if banco:
        print('• carregando a cópia do banco a partir do backup...')
        with open(banco, 'rb') as f:
            mysql('casacapital', entrada=f.read())

    print('• subindo o sistema na porta 8090...')
    ambiente = dict(os.environ,
                    SPRING_DATASOURCE_URL=f'jdbc:mysql://127.0.0.1:{PORTA_BANCO}/casacapital?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true',
                    DB_USERNAME=USUARIO_BANCO, DB_PASSWORD=SENHA_BANCO, SERVER_PORT=str(PORTA_APP),
                    JWT_SECRET=SEGREDO_JWT, MAIL_USERNAME='', MAIL_PASSWORD='', BACKUP_DIR='')
    app_dir = os.path.join(PASTA_ESTADO, 'app')
    os.makedirs(app_dir, exist_ok=True)
    proc = subprocess.Popen([JAVA, '-jar', jar], cwd=app_dir, env=ambiente,
                            stdout=open(os.path.join(app_dir, 'saida.log'), 'wb'), stderr=subprocess.STDOUT)
    with open(os.path.join(PASTA_ESTADO, 'app.pid'), 'w') as f:
        f.write(str(proc.pid))

    def respondendo():
        if proc.poll() is not None:
            raise RuntimeError('O sistema parou ao subir; veja ' + os.path.join(app_dir, 'saida.log'))
        try:
            urllib.request.urlopen(f'http://localhost:{PORTA_APP}/index.html', timeout=2)
            return True
        except Exception:
            return False
    esperar(respondendo, 180, 'O sistema')


def limpar():
    print('• desligando e apagando o ambiente de teste...')
    pid = os.path.join(PASTA_ESTADO, 'app.pid')
    if os.path.exists(pid):
        subprocess.run(['taskkill', '/F', '/T', '/PID', open(pid).read().strip()], capture_output=True)
    if not porta_livre(PORTA_BANCO):
        subprocess.run([os.path.join(MYSQL_BIN, 'mysqladmin.exe'), '-u', 'root', '-h', '127.0.0.1',
                        '-P', str(PORTA_BANCO), 'shutdown'], capture_output=True)
        esperar(lambda: porta_livre(PORTA_BANCO), 60, 'O desligamento do MySQL temporário')
    time.sleep(2)
    shutil.rmtree(PASTA_ESTADO, ignore_errors=True)
    print('  apagado' if not os.path.exists(PASTA_ESTADO) else '  ATENÇÃO: não deu para apagar ' + PASTA_ESTADO)


def erros_no_log():
    log = os.path.join(PASTA_ESTADO, 'app', 'logs', 'casacapital.log')
    if not os.path.exists(log):
        return []
    linhas = open(log, encoding='utf-8', errors='replace').read().splitlines()
    # sem e-mail configurado no teste, a falha de envio do "esqueci a senha" é esperada
    return [l for l in linhas if ' ERROR ' in l and 'e-mail de redefinição' not in l]


def main():
    p = argparse.ArgumentParser(description='Teste completo do CasaCapital num ambiente separado.')
    p.add_argument('--banco', help='backup .sql do banco da casa para testar com os dados reais (cópia)')
    p.add_argument('--jar', default=os.path.join(RAIZ, 'dist', 'CasaCapital-Instalador', 'app', 'casacapital.jar'))
    p.add_argument('--manter', action='store_true', help='deixa ligado para testar as telas no navegador')
    p.add_argument('--limpar', action='store_true', help='só desliga e apaga um ambiente deixado ligado')
    a = p.parse_args()
    if a.limpar:
        limpar()
        return 0

    ok = True
    try:
        subir(a.jar, a.banco)
        aqui = os.path.dirname(os.path.abspath(__file__))
        ambiente = dict(os.environ, PYTHONIOENCODING='utf-8')
        print('\n══ Roteiro de verificações da API ══')
        ok &= subprocess.run([sys.executable, '-I', os.path.join(aqui, 'teste_api.py')], env=ambiente).returncode == 0
        if a.banco:
            print('\n══ Dados reais (cópia do banco) ══')
            ok &= subprocess.run([sys.executable, '-I', os.path.join(aqui, 'teste_dados_reais.py')], env=ambiente).returncode == 0
        erros = erros_no_log()
        print(f'\n══ Log do sistema: {len(erros)} erro(s) interno(s) ══')
        for linha in erros[:10]:
            print('  ', linha[:220])
        ok &= not erros
        if a.manter:
            print('\nLigado em http://localhost:8090 — ao terminar: python testes/teste_completo.py --limpar')
    finally:
        if not a.manter:
            limpar()
    print('\nRESULTADO:', 'TUDO CERTO' if ok else 'HÁ FALHAS (veja acima)')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
