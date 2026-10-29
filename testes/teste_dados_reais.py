"""Abre todas as listas e relatórios de cada login da CÓPIA do banco da casa (servidor de teste, porta 8090).
Só leitura (GET). Mostra apenas códigos de resposta — nunca valores ou nomes."""
import base64, hashlib, hmac, json, subprocess, sys, time, urllib.request, urllib.error
from datetime import date, timedelta

sys.stdout.reconfigure(encoding='utf-8', errors='replace')  # console do Windows (cp1252)
BASE = 'http://localhost:8090/api'
assert '8090' in BASE  # nunca contra o sistema da casa
SEGREDO = b'segredo-so-para-o-teste-local-0123456789abcdefghij'
MYSQL = r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'


def b64(x):
    return base64.urlsafe_b64encode(x).rstrip(b'=')


def token(uid, fam, email, plano, perfil, membro):
    agora = int(time.time())
    cab = b64(json.dumps({'alg': 'HS384'}).encode())
    corpo = b64(json.dumps({'sub': str(uid), 'familiaId': fam, 'email': email, 'plano': plano, 'perfil': perfil,
                            'membroId': membro, 'iat': agora, 'exp': agora + 3600}).encode())
    assinatura = b64(hmac.new(SEGREDO, cab + b'.' + corpo, hashlib.sha384).digest())
    return (cab + b'.' + corpo + b'.' + assinatura).decode()


def get(caminho, tk):
    req = urllib.request.Request(BASE + caminho, headers={'Authorization': 'Bearer ' + tk})
    try:
        with urllib.request.urlopen(req) as r:
            r.read()
            return r.status
    except urllib.error.HTTPError as e:
        return e.code


saida = subprocess.run([MYSQL, '-u', 'root', '-h', '127.0.0.1', '-P', '3307', 'casacapital', '-N', '-e',
                        "SELECT u.id, u.familia_id, u.email, f.plano, u.perfil, IFNULL(u.membro_id, 'NULL') "
                        "FROM usuario u JOIN familia f ON f.id = u.familia_id"],
                       capture_output=True, text=True, encoding='utf-8').stdout
usuarios = [l.split('\t') for l in saida.strip().splitlines()]

hoje = date.today()
meses = [(hoje.replace(day=1) - timedelta(days=30 * i)).strftime('%Y-%m') for i in range(0, 13)]
PAIS = ['/familia/me', '/receitas', '/despesas', '/contas', '/compras', '/dispensa', '/tarefas', '/membros', '/metas',
        '/historico-precos', '/mercados/comparacao', '/compromissos', '/compromissos/alertas', '/investimentos',
        '/investimentos/cdi', '/assinaturas', '/planejamento/patrimonio', '/planejamento/fluxo-caixa?meses=6',
        '/planejamento/fluxo-caixa?meses=12', '/fechamento/preferencias', '/imposto-renda', '/imposto-renda?ano=2025',
        '/nfce', '/mesada', '/backup/status', '/rede', '/familia/foto', '/analises/resumo', '/alertas'] + \
       [f'/orcamentos/{m}' for m in meses] + [f'/fechamento?mes={m}' for m in meses]
FILHO = ['/familia/me', '/mesada/minha', '/tarefas', '/compras', '/compromissos', '/compromissos/alertas', '/familia/foto']
ESPERADOS = {200, 204}

problemas, total = [], 0
for uid, fam, email, plano, perfil, membro in usuarios:
    tk = token(int(uid), int(fam), email, plano, perfil, None if membro == 'NULL' else int(membro))
    for caminho in (FILHO if perfil == 'DEPENDENTE' else PAIS):
        st = get(caminho, tk)
        total += 1
        ok = st in ESPERADOS or (caminho == '/familia/foto' and st == 404) \
            or (caminho in ('/analises/resumo', '/alertas') and plano != 'PREMIUM' and st == 403)
        if not ok:
            problemas.append(f'família {fam} ({perfil.lower()}): GET {caminho} -> {st}')
    # histórico de cada produto da família
    if perfil != 'DEPENDENTE':
        req = urllib.request.Request(BASE + '/historico-precos', headers={'Authorization': 'Bearer ' + tk})
        try:
            with urllib.request.urlopen(req) as r:
                for p in json.loads(r.read()):
                    st = get('/historico-precos/' + urllib.request.quote(p['produtoChave']), tk)
                    total += 1
                    if st != 200:
                        problemas.append(f'família {fam}: histórico de um produto -> {st}')
        except urllib.error.HTTPError as e:
            problemas.append(f'família {fam}: lista de preços -> {e.code}')

print(f'{len(usuarios)} logins ({sum(1 for u in usuarios if u[4] == "DEPENDENTE")} de filhos), {total} consultas, '
      f'{len(problemas)} problemas')
for p in problemas:
    print(' -', p)

sys.exit(1 if problemas else 0)
