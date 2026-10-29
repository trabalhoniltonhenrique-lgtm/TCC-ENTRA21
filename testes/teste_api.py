"""Teste completo da API do CasaCapital contra o servidor de TESTE (porta 8090, banco temporário na 3307)."""
import json, sys, urllib.request, urllib.error, uuid, io, struct, zlib
from datetime import date, datetime, timedelta

sys.stdout.reconfigure(encoding='utf-8', errors='replace')  # console do Windows (cp1252)
BASE = 'http://localhost:8090/api'
assert '8090' in BASE  # nunca contra o sistema da casa
HOJE = date.today()
MES = HOJE.strftime('%Y-%m')
d = lambda dias: (HOJE + timedelta(days=dias)).isoformat()

resultados = []  # (grupo, nome, ok, detalhe)
grupo_atual = ['']


def grupo(nome):
    grupo_atual[0] = nome


def chamar(metodo, caminho, token=None, corpo=None, multipart=None):
    headers = {}
    dados = None
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if multipart is not None:
        limite = 'limite' + uuid.uuid4().hex
        partes = []
        for nome, (arquivo, tipo, conteudo) in multipart.items():
            if arquivo is None:  # campo de texto comum
                partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{nome}"\r\n\r\n'.encode() + conteudo + b'\r\n')
            else:
                partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{nome}"; filename="{arquivo}"\r\n'
                              f'Content-Type: {tipo}\r\n\r\n'.encode() + conteudo + b'\r\n')
        dados = b''.join(partes) + f'--{limite}--\r\n'.encode()
        headers['Content-Type'] = 'multipart/form-data; boundary=' + limite
    elif corpo is not None:
        dados = json.dumps(corpo).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + caminho, data=dados, method=metodo, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            bruto = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        bruto = e.read()
        status = e.code
    try:
        conteudo = json.loads(bruto) if bruto else None
    except Exception:
        conteudo = bruto
    return status, conteudo


def checar(nome, condicao, detalhe=''):
    resultados.append((grupo_atual[0], nome, bool(condicao), detalhe))
    return condicao


def espera(nome, metodo, caminho, token=None, corpo=None, status=200, multipart=None):
    st, resp = chamar(metodo, caminho, token, corpo, multipart)
    esperados = status if isinstance(status, (list, tuple, set)) else [status]
    checar(nome, st in esperados, f'{metodo} {caminho} -> {st} (esperado {status}) {str(resp)[:200]}')
    return resp


def png(largura=40, altura=30, transparente=False):
    linhas = b''.join(b'\x00' + (b'\x00\x00\x00\x00' if transparente else b'\xff\x80\x00\xff') * largura for _ in range(altura))
    def bloco(tipo, dados):
        return struct.pack('>I', len(dados)) + tipo + dados + struct.pack('>I', zlib.crc32(tipo + dados) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + bloco(b'IHDR', struct.pack('>IIBBBBB', largura, altura, 8, 6, 0, 0, 0))
            + bloco(b'IDAT', zlib.compress(linhas)) + bloco(b'IEND', b''))


# ════════════════ Cadastro e login ════════════════
grupo('Cadastro e login')
sufixo = uuid.uuid4().hex[:6]
email_a = f'pai.{sufixo}@teste.com'
r = espera('cadastrar família A', 'POST', '/auth/registrar', corpo={'nomeFamilia': 'Família A', 'nome': 'Pai A', 'email': email_a, 'senha': 'senha-a-123'}, status=201)
A = r['token'] if r else None
r = espera('cadastrar família B', 'POST', '/auth/registrar', corpo={'nomeFamilia': 'Família B', 'nome': 'Mãe B', 'email': f'mae.{sufixo}@teste.com', 'senha': 'senha-b-123'}, status=201)
B = r['token'] if r else None
espera('e-mail repetido é recusado (409)', 'POST', '/auth/registrar', corpo={'nomeFamilia': 'X', 'nome': 'X', 'email': email_a.upper(), 'senha': 'senha-x-123'}, status=409)
espera('senha curta é recusada', 'POST', '/auth/registrar', corpo={'nomeFamilia': 'X', 'nome': 'X', 'email': 'x.' + email_a, 'senha': '123'}, status=400)
espera('e-mail inválido é recusado', 'POST', '/auth/registrar', corpo={'nomeFamilia': 'X', 'nome': 'X', 'email': 'nao-e-email', 'senha': 'senha-x-123'}, status=400)
espera('login com e-mail em maiúsculas', 'POST', '/auth/login', corpo={'email': email_a.upper(), 'senha': 'senha-a-123'})
espera('senha errada dá 401', 'POST', '/auth/login', corpo={'email': email_a, 'senha': 'errada'}, status=401)
espera('usuário inexistente dá 401', 'POST', '/auth/login', corpo={'email': 'ninguem@teste.com', 'senha': 'x'}, status=401)
espera('login sem corpo dá 400', 'POST', '/auth/login', corpo={}, status=400)
espera('esqueci a senha sem e-mail configurado avisa (503, não 500)', 'POST', '/auth/esqueci-senha', corpo={'email': email_a}, status=503)
espera('esqueci a senha de e-mail inexistente responde igual', 'POST', '/auth/esqueci-senha', corpo={'email': 'ninguem@teste.com'}, status=[200, 204])
espera('redefinir com token inválido é recusado', 'POST', '/auth/redefinir-senha', corpo={'token': 'abc', 'novaSenha': 'nova-senha-1'}, status=[400, 401, 404, 410])
espera('sem login não acessa a API', 'GET', '/despesas', status=401)
espera('token falso não acessa a API', 'GET', '/despesas', token='abc.def.ghi', status=401)

# ════════════════ Família / configurações ════════════════
grupo('Família e configurações')
fam = espera('ver família', 'GET', '/familia/me', A)
espera('salvar configurações', 'PUT', '/familia/me', A, {'nomeFamilia': 'Família A Silva', 'cidade': 'Florianópolis - SC', 'responsavel': 'Pai A', 'moeda': 'R$', 'separadorDecimal': ',', 'diaFechamento': 5, 'mostrarSaldo': True, 'alertaContas': True, 'confirmarExclusao': True, 'agruparCat': False, 'corPrimaria': '#16a34a'})
r = espera('cor fica em maiúsculas', 'GET', '/familia/me', A)
checar('cor salva como #16A34A', r and r.get('corPrimaria') == '#16A34A', str(r and r.get('corPrimaria')))
espera('cor inválida é recusada', 'PUT', '/familia/me', A, {'nomeFamilia': 'Família A', 'corPrimaria': 'verde'}, status=400)
espera('dia de fechamento 32 é recusado', 'PUT', '/familia/me', A, {'nomeFamilia': 'Família A', 'diaFechamento': 32}, status=400)
espera('nome da família vazio é recusado', 'PUT', '/familia/me', A, {'nomeFamilia': ''}, status=400)
espera('mudar para Premium', 'PUT', '/familia/plano', A, {'plano': 'PREMIUM'})
espera('plano inexistente é recusado', 'PUT', '/familia/plano', A, {'plano': 'OURO'}, status=400)
espera('enviar foto PNG', 'POST', '/familia/foto', A, multipart={'foto': ('f.png', 'image/png', png())})
espera('enviar foto transparente', 'POST', '/familia/foto', A, multipart={'foto': ('f.png', 'image/png', png(transparente=True))})
st, _ = chamar('GET', '/familia/foto', A)
checar('baixar foto', st == 200, str(st))
espera('arquivo que não é imagem é recusado', 'POST', '/familia/foto', A, multipart={'foto': ('f.txt', 'text/plain', b'oi')}, status=400)
espera('família B não tem foto (404)', 'GET', '/familia/foto', B, status=404)
espera('status do backup', 'GET', '/backup/status', A)
espera('endereços de rede', 'GET', '/rede', A)

# ════════════════ Membros ════════════════
grupo('Membros')
m1 = espera('cadastrar membro', 'POST', '/membros', A, {'nome': 'Ana', 'parentesco': 'Mãe', 'nascimento': '1985-03-02', 'renda': 5200, 'obs': '', 'cor': '#DB2777'}, status=[200, 201])
m2 = espera('cadastrar filho', 'POST', '/membros', A, {'nome': 'Joãozinho', 'parentesco': 'Filho', 'nascimento': '2014-07-11', 'renda': 0, 'obs': '', 'cor': '#2563EB'}, status=[200, 201])
m3 = espera('cadastrar outro membro (para excluir)', 'POST', '/membros', A, {'nome': 'Vovó', 'parentesco': 'Avó', 'renda': 0}, status=[200, 201])
M1, M2, M3 = (m1 or {}).get('id'), (m2 or {}).get('id'), (m3 or {}).get('id')
espera('membro sem nome é recusado', 'POST', '/membros', A, {'nome': '', 'parentesco': 'Filho'}, status=400)
espera('editar membro', 'PUT', f'/membros/{M1}', A, {'nome': 'Ana Maria', 'parentesco': 'Mãe', 'renda': 5300, 'cor': '#DB2777'})
espera('família B não vê membro de A', 'PUT', f'/membros/{M1}', B, {'nome': 'Invasor', 'parentesco': 'X'}, status=[403, 404])
espera('família B não exclui membro de A', 'DELETE', f'/membros/{M1}', B, status=[403, 404])

# ════════════════ Receitas e despesas ════════════════
grupo('Receitas e despesas')
r1 = espera('lançar receita', 'POST', '/receitas', A, {'nome': 'Salário', 'valor': 5200, 'categoria': 'Salário', 'data': d(-2), 'membroId': M1}, status=[200, 201])
R1 = (r1 or {}).get('id')
espera('receita com categoria de despesa é recusada', 'POST', '/receitas', A, {'nome': 'X', 'valor': 10, 'categoria': 'Alimentação', 'data': d(0)}, status=400)
espera('receita com valor zero é recusada', 'POST', '/receitas', A, {'nome': 'X', 'valor': 0, 'categoria': 'Outros', 'data': d(0)}, status=400)
espera('receita com valor negativo é recusada', 'POST', '/receitas', A, {'nome': 'X', 'valor': -5, 'categoria': 'Outros', 'data': d(0)}, status=400)
espera('receita sem data é recusada', 'POST', '/receitas', A, {'nome': 'X', 'valor': 5, 'categoria': 'Outros'}, status=400)
espera('receita com data inválida é recusada', 'POST', '/receitas', A, {'nome': 'X', 'valor': 5, 'categoria': 'Outros', 'data': '2026-13-45'}, status=400)
espera('receita com membro de outra família é recusada', 'POST', '/receitas', B, {'nome': 'X', 'valor': 5, 'categoria': 'Outros', 'data': d(0), 'membroId': M1}, status=404)
de1 = espera('lançar despesa', 'POST', '/despesas', A, {'nome': 'Mercado', 'valor': 312.45, 'categoria': 'Alimentação', 'data': d(-1), 'membroId': M2}, status=[200, 201])
de2 = espera('lançar despesa do membro que será excluído', 'POST', '/despesas', A, {'nome': 'Remédio', 'valor': 40, 'categoria': 'Saúde', 'data': d(-1), 'membroId': M3}, status=[200, 201])
D1 = (de1 or {}).get('id')
espera('despesa com valor gigante', 'POST', '/despesas', A, {'nome': 'Casa', 'valor': 999999999.99, 'categoria': 'Moradia', 'data': d(0)}, status=[200, 201, 400])
espera('despesa com valor acima do limite do banco é recusada (não 500)', 'POST', '/despesas', A, {'nome': 'Erro', 'valor': 1e15, 'categoria': 'Moradia', 'data': d(0)}, status=400)
espera('despesa com 3 casas decimais', 'POST', '/despesas', A, {'nome': 'Gasolina', 'valor': 10.555, 'categoria': 'Transporte', 'data': d(0)}, status=[200, 201, 400])
espera('despesa com nome enorme é recusada (não 500)', 'POST', '/despesas', A, {'nome': 'x' * 5000, 'valor': 1, 'categoria': 'Outros', 'data': d(0)}, status=400)
espera('editar despesa', 'PUT', f'/despesas/{D1}', A, {'nome': 'Mercado grande', 'valor': 400, 'categoria': 'Alimentação', 'data': d(-1), 'membroId': None})
espera('família B não edita despesa de A', 'PUT', f'/despesas/{D1}', B, {'nome': 'X', 'valor': 1, 'categoria': 'Outros', 'data': d(0)}, status=404)
espera('família B não exclui despesa de A', 'DELETE', f'/despesas/{D1}', B, status=404)
espera('família B não exclui receita de A', 'DELETE', f'/receitas/{R1}', B, status=404)
lst = espera('família B não vê as despesas de A', 'GET', '/despesas', B)
checar('lista de B vazia', lst == [], str(lst)[:100])
espera('excluir despesa inexistente dá 404', 'DELETE', '/despesas/999999', A, status=404)
espera('id que não é número dá 400', 'DELETE', '/despesas/abc', A, status=400)

# ════════════════ Contas ════════════════
grupo('Contas a pagar')
c = espera('conta mensal', 'POST', '/contas', A, {'descricao': 'Internet', 'categoria': 'Internet / TV', 'valor': 119.9, 'vencimento': d(3), 'recorrencia': 'MENSAL', 'cartao': 'Nubank'}, status=[200, 201])
C1 = c[0]['id'] if isinstance(c, list) and c else (c or {}).get('id')
p = espera('parcelado pelo total', 'POST', '/contas', A, {'descricao': 'Geladeira', 'categoria': 'Outros', 'valor': 100, 'vencimento': d(5), 'recorrencia': 'PARCELADA', 'qtdParcelas': 3, 'tipoValorParcela': 'total'}, status=[200, 201])
if isinstance(p, list):
    checar('parcelas somam o total', abs(sum(x['valor'] for x in p) - 100) < 0.001, str([x['valor'] for x in p]))
espera('parcelado com 1 parcela é recusado', 'POST', '/contas', A, {'descricao': 'X', 'categoria': 'Outros', 'valor': 10, 'vencimento': d(5), 'recorrencia': 'PARCELADA', 'qtdParcelas': 1}, status=400)
espera('parcelado com 1000 parcelas é recusado (não 500)', 'POST', '/contas', A, {'descricao': 'X', 'categoria': 'Outros', 'valor': 10, 'vencimento': d(5), 'recorrencia': 'PARCELADA', 'qtdParcelas': 1000}, status=400)
espera('recorrência inexistente é recusada', 'POST', '/contas', A, {'descricao': 'X', 'categoria': 'Outros', 'valor': 10, 'vencimento': d(5), 'recorrencia': 'SEMANAL'}, status=400)
espera('pagar conta', 'POST', f'/contas/{C1}/marcar-pago', A)
espera('pagar de novo', 'POST', f'/contas/{C1}/marcar-pago', A)
todas = espera('listar contas', 'GET', '/contas', A) or []
internet = [x for x in todas if x['descricao'] == 'Internet']
checar('só uma "próxima" Internet', len(internet) == 2, f'{len(internet)} contas Internet')
desp = espera('despesas', 'GET', '/despesas', A) or []
checar('uma despesa só do pagamento', len([x for x in desp if x['nome'] == 'Internet']) == 1, str(len([x for x in desp if x['nome'] == 'Internet'])))
espera('reabrir', 'POST', f'/contas/{C1}/reabrir', A)
espera('pagar após reabrir', 'POST', f'/contas/{C1}/marcar-pago', A)
todas = espera('listar contas de novo', 'GET', '/contas', A) or []
checar('continua só uma "próxima" Internet', len([x for x in todas if x['descricao'] == 'Internet']) == 2, '')
abertas = [x['id'] for x in todas if not x['pago']]
espera('pagar em lote', 'POST', '/contas/marcar-pago', A, {'ids': abertas[:2]})
espera('lote vazio é recusado', 'POST', '/contas/marcar-pago', A, {'ids': []}, status=400)
espera('família B não paga conta de A', 'POST', f'/contas/{C1}/marcar-pago', B, status=404)
espera('família B não paga em lote conta de A', 'POST', '/contas/marcar-pago', B, {'ids': [C1]}, status=404)
if isinstance(p, list):
    espera('excluir parcelado inteiro', 'DELETE', f'/contas/{p[1]["id"]}?grupo=true', A, status=[200, 204])

# ════════════════ Compras, despensa, histórico ════════════════
grupo('Compras, despensa e preços')
cp = espera('item de compra', 'POST', '/compras', A, {'nome': 'Arroz 5kg', 'qtd': 2, 'categoria': 'Geral', 'frequencia': 'MENSAL'}, status=[200, 201])
CP = (cp or {}).get('id')
espera('qtd zero é recusada', 'POST', '/compras', A, {'nome': 'X', 'qtd': 0, 'categoria': 'Geral'}, status=400)
espera('comprar com preço', 'POST', f'/compras/{CP}/comprar', A, {'precoUnitario': 27.5})
cp2 = espera('outro item', 'POST', '/compras', A, {'nome': 'Café', 'qtd': 1, 'categoria': 'Geral', 'frequencia': 'SEM_FREQUENCIA'}, status=[200, 201])
espera('comprar sem preço', 'POST', f'/compras/{(cp2 or {}).get("id")}/comprar', A)
espera('comprar com preço negativo é recusado', 'POST', '/compras/1/comprar', A, {'precoUnitario': -3}, status=[400, 404])
hist = espera('histórico de preços', 'GET', '/historico-precos', A) or []
checar('arroz tem preço no histórico', any(h.get('produtoChave') == 'arroz 5kg' for h in hist), str(hist)[:150])
espera('histórico de um produto', 'GET', '/historico-precos/arroz%205kg', A)
espera('histórico de produto inexistente', 'GET', '/historico-precos/naoexiste', A, status=[200, 404])
disp = espera('despensa', 'GET', '/dispensa', A) or []
DI = disp[0]['id'] if disp else None
checar('item comprado foi para a despensa', bool(disp), str(disp)[:120])
if DI:
    espera('aumentar qtd', 'POST', f'/dispensa/{DI}/qtd?delta=1', A)
    espera('diminuir muito a qtd', 'POST', f'/dispensa/{DI}/qtd?delta=-50', A, status=[200, 204, 400])
    espera('família B não mexe na despensa de A', 'POST', f'/dispensa/{DI}/qtd?delta=1', B, status=404)
espera('item manual na despensa', 'POST', '/dispensa', A, {'nome': 'Feijão', 'qtd': 3, 'categoria': 'Geral'}, status=[200, 201])
disp = espera('despensa de novo', 'GET', '/dispensa', A) or []
if disp:
    espera('mover para compras', 'POST', f'/dispensa/{disp[-1]["id"]}/mover-para-compras', A, status=[200, 204])
espera('comparação de mercados', 'GET', '/mercados/comparacao', A)
espera('família B não vê compra de A', 'DELETE', f'/compras/{CP}', B, status=404)

# ════════════════ Tarefas e compromissos ════════════════
grupo('Tarefas e compromissos')
t = espera('tarefa', 'POST', '/tarefas', A, {'nome': 'Levar o lixo', 'prazo': d(-1), 'membroId': M2}, status=[200, 201])
T = (t or {}).get('id')
espera('tarefa no dia da semana 8 é recusada', 'POST', '/tarefas', A, {'nome': 'X', 'diaSemana': 8}, status=400)
espera('editar tarefa', 'PUT', f'/tarefas/{T}', A, {'nome': 'Levar o lixo reciclável', 'diaSemana': 2, 'membroId': M2})
espera('concluir tarefa', 'POST', f'/tarefas/{T}/concluir', A)
espera('família B não conclui tarefa de A', 'POST', f'/tarefas/{T}/concluir', B, status=404)
inicio = (datetime.now() + timedelta(minutes=10)).replace(second=0, microsecond=0).isoformat()
cm = espera('compromisso', 'POST', '/compromissos', A, {'titulo': 'Dentista', 'inicio': inicio, 'local': 'Centro', 'antecedenciaMin': 15, 'membroId': M2}, status=[200, 201])
CM = (cm or {}).get('id')
al = espera('alertas de compromisso', 'GET', '/compromissos/alertas', A) or []
checar('dentista aparece no alerta', any(x.get('id') == CM for x in al), str(al)[:150])
espera('adiar 5 min', 'POST', f'/compromissos/{CM}/adiar?minutos=5', A, status=[200, 204])
espera('adiar com minutos negativos vira 1 minuto (sem erro)', 'POST', f'/compromissos/{CM}/adiar?minutos=-5', A, status=[200, 204])
espera('ciente', 'POST', f'/compromissos/{CM}/ciente', A, status=[200, 204])
espera('compromisso sem data é recusado', 'POST', '/compromissos', A, {'titulo': 'X'}, status=400)
espera('antecedência de 8 dias é recusada', 'POST', '/compromissos', A, {'titulo': 'X', 'inicio': inicio, 'antecedenciaMin': 20000}, status=400)
espera('família B não edita compromisso de A', 'PUT', f'/compromissos/{CM}', B, {'titulo': 'X', 'inicio': inicio}, status=404)

# ════════════════ Metas, orçamento, investimentos ════════════════
grupo('Metas, orçamento e investimentos')
mt = espera('meta', 'POST', '/metas', A, {'nome': 'Viagem', 'valorAlvo': 1000, 'prazo': d(200)}, status=[200, 201])
MT = (mt or {}).get('id')
espera('aporte', 'POST', f'/metas/{MT}/aporte', A, {'valor': 1000})
r = espera('meta concluída', 'GET', '/metas', A) or []
checar('meta marcada como concluída', any(x['id'] == MT and x['concluida'] for x in r), '')
espera('subir o alvo', 'PUT', f'/metas/{MT}', A, {'nome': 'Viagem', 'valorAlvo': 1500, 'prazo': d(200)})
r = espera('meta reaberta', 'GET', '/metas', A) or []
checar('meta voltou a ficar em andamento', any(x['id'] == MT and not x['concluida'] for x in r), '')
espera('aporte zero é recusado', 'POST', f'/metas/{MT}/aporte', A, {'valor': 0}, status=400)
espera('família B não aporta na meta de A', 'POST', f'/metas/{MT}/aporte', B, {'valor': 10}, status=404)
espera('orçamento do mês', 'PUT', f'/orcamentos/{MES}', A, {'categoria': 'Alimentação', 'limite': 900})
espera('orçamento com categoria inválida é recusado', 'PUT', f'/orcamentos/{MES}', A, {'categoria': 'Nada', 'limite': 10}, status=400)
espera('orçamento com mês inválido é recusado', 'PUT', '/orcamentos/2026-13', A, {'categoria': 'Lazer', 'limite': 10}, status=400)
espera('orçamento com mês sem formato é recusado', 'GET', '/orcamentos/abc', A, status=400)
espera('copiar mês anterior sem orçamento anterior avisa (404)', 'POST', f'/orcamentos/{MES}/copiar-mes-anterior', A, status=404)
espera('excluir orçamento', 'DELETE', f'/orcamentos/{MES}/Alimenta%C3%A7%C3%A3o', A, status=[200, 204])
espera('CDI', 'PUT', '/investimentos/cdi', A, {'cdiAnual': 10.65})
inv = espera('investimento ligado à meta', 'POST', '/investimentos', A, {'nome': 'CDB Banco X', 'tipo': 'CDB', 'instituicao': 'Banco X', 'valorAplicado': 1000, 'valorAtual': 1010, 'taxa': 110, 'tipoTaxa': 'CDI', 'dataAplicacao': d(-30), 'vencimento': d(700), 'metaId': MT}, status=[200, 201])
INV = (inv or {}).get('id')
espera('tipo de investimento inválido é recusado', 'POST', '/investimentos', A, {'nome': 'X', 'tipo': 'Ouro', 'valorAplicado': 10, 'dataAplicacao': d(0)}, status=400)
espera('investimento com meta de outra família é recusado', 'POST', '/investimentos', B, {'nome': 'X', 'tipo': 'CDB', 'valorAplicado': 10, 'dataAplicacao': d(0), 'metaId': MT}, status=[400, 404])
espera('atualizar valor atual', 'PUT', f'/investimentos/{INV}/valor-atual', A, {'valorAtual': 1020})
espera('excluir meta que tem investimento ligado', 'DELETE', f'/metas/{MT}', A, status=[200, 204])
r = espera('investimentos depois de excluir a meta', 'GET', '/investimentos', A) or []
checar('investimento continua, sem meta', any(x['id'] == INV and not x.get('metaId') for x in r), str(r)[:150])

# ════════════════ Alertas, análises, relatórios ════════════════
grupo('Alertas, análises e relatórios')
espera('alerta de limite', 'POST', '/alertas', A, {'categoria': 'Alimentação', 'limite': 300}, status=[200, 201])
espera('alerta com categoria inválida é recusado', 'POST', '/alertas', A, {'categoria': 'Nada', 'limite': 300}, status=400)
espera('excluir alerta', 'DELETE', '/alertas/Alimenta%C3%A7%C3%A3o', A, status=[200, 204])
espera('análises (Premium)', 'GET', '/analises/resumo', A)
espera('análises bloqueadas no Essencial', 'GET', '/analises/resumo', B, status=403)
espera('alertas bloqueados no Essencial', 'GET', '/alertas', B, status=403)
espera('assinaturas', 'GET', '/assinaturas', A)
espera('ignorar assinatura', 'POST', '/assinaturas/ignoradas', A, {'chave': 'netflix'}, status=[200, 204])
espera('desfazer ignorar', 'DELETE', '/assinaturas/ignoradas?chave=netflix', A, status=[200, 204])
espera('ignorar sem chave é recusado', 'POST', '/assinaturas/ignoradas', A, {}, status=400)
espera('fluxo de caixa', 'GET', '/planejamento/fluxo-caixa?meses=6', A)
espera('fluxo de caixa com meses absurdos', 'GET', '/planejamento/fluxo-caixa?meses=100000', A, status=[200, 400])
espera('patrimônio', 'GET', '/planejamento/patrimonio', A)
espera('fechamento do mês', 'GET', '/fechamento', A)
espera('fechamento de mês inválido é recusado', 'GET', '/fechamento?mes=abc', A, status=400)
espera('preferências do fechamento', 'GET', '/fechamento/preferencias', A)
espera('ativar e-mail do fechamento', 'PUT', '/fechamento/preferencias', A, {'emailAtivo': True})
espera('enviar fechamento sem e-mail configurado não dá 500', 'POST', '/fechamento/enviar', A, status=[200, 400, 503])
espera('imposto de renda', 'GET', '/imposto-renda', A)
espera('imposto de renda de outro ano', 'GET', '/imposto-renda?ano=2025', A)
espera('notas fiscais importadas', 'GET', '/nfce', A)

# ════════════════ Importações (fatura e nota) ════════════════
grupo('Importação de fatura e nota')
espera('analisar arquivo que não é PDF', 'POST', '/faturas/analisar', A, multipart={'arquivo': ('x.pdf', 'application/pdf', b'nao e pdf')}, status=400)
espera('importar fatura', 'POST', '/faturas/importar', A, {'vencimento': d(10), 'cartao': 'Itaú', 'itens': [{'data': d(-5), 'descricao': 'IFOOD *PEDIDO', 'valor': 52.9, 'categoria': 'Alimentação'}, {'data': d(-4), 'descricao': 'NETFLIX', 'valor': 39.9, 'categoria': 'Lazer'}]}, status=[200, 201])
espera('importar fatura sem itens é recusado', 'POST', '/faturas/importar', A, {'vencimento': d(10), 'cartao': 'Itaú', 'itens': []}, status=400)
espera('importar conta de luz', 'POST', '/faturas/importar-conta', A, {'descricao': 'Luz Celesc', 'categoria': 'Energia', 'valor': 245.9, 'vencimento': d(8)}, status=[200, 201])
espera('ler nota sem nada é recusado', 'POST', '/nfce/ler', A, multipart={'texto': (None, None, b'')}, status=400)
espera('ler nota com link de fora do .gov.br é recusado', 'POST', '/nfce/ler', A, multipart={'link': (None, None, b'http://127.0.0.1:8080/api/familia/me')}, status=400)
chave = '42' + '0' * 42
espera('importar nota', 'POST', '/nfce/importar', A, {'chave': chave, 'emitente': 'Supermercado Teste', 'cnpj': '12.345.678/0001-90', 'dataEmissao': d(-1), 'total': 31.4, 'itens': [{'nome': 'LEITE INTEGRAL 1L', 'codigo': '7891000100103', 'valorUnitario': 5.49, 'registrarPreco': True, 'despensa': True, 'qtdDespensa': 2, 'categoria': 'Laticínios'}], 'lancarDespesa': True, 'categoriaDespesa': 'Alimentação'}, status=[200, 201])
espera('importar a mesma nota de novo é recusado', 'POST', '/nfce/importar', A, {'chave': chave, 'emitente': 'Supermercado Teste', 'dataEmissao': d(-1), 'total': 31.4, 'itens': [{'nome': 'LEITE', 'valorUnitario': 5.49, 'registrarPreco': True, 'despensa': False}], 'lancarDespesa': False}, status=409)

# ════════════════ Mesada e login do filho ════════════════
grupo('Mesada e login do filho')
espera('configurar mesada', 'PUT', f'/mesada/{M2}', A, {'valor': 50, 'diaPagamento': 5, 'ativa': True, 'metaNome': 'Bicicleta', 'metaValor': 600})
espera('mesada com dia 40 é recusada', 'PUT', f'/mesada/{M2}', A, {'valor': 50, 'diaPagamento': 40, 'ativa': True}, status=400)
espera('extra', 'POST', f'/mesada/{M2}/movimentos', A, {'tipo': 'EXTRA', 'valor': 20, 'descricao': 'Tirou 10', 'data': d(0)}, status=[200, 201])
login_filho = 'joao' + sufixo
espera('criar login do filho', 'PUT', f'/mesada/{M2}/acesso', A, {'login': login_filho, 'senha': 'senha-joao'})
espera('login de filho com espaço é recusado', 'PUT', f'/mesada/{M2}/acesso', A, {'login': 'joao silva', 'senha': 'senha-joao'}, status=400)
espera('família B não vê mesada de A', 'GET', f'/mesada/{M2}', B, status=404)
r = espera('filho entra', 'POST', '/auth/login', corpo={'email': login_filho, 'senha': 'senha-joao'})
F = (r or {}).get('token')
espera('filho vê a própria mesada', 'GET', '/mesada/minha', F)
espera('filho anota gasto', 'POST', '/mesada/minha/gastos', F, {'valor': 7.5, 'descricao': 'Figurinhas'}, status=[200, 201])
espera('filho não gasta mais do que tem', 'POST', '/mesada/minha/gastos', F, {'valor': 100000, 'descricao': 'Videogame'}, status=400)
espera('filho vê as tarefas dele', 'GET', '/tarefas', F)
espera('filho pede item de compra', 'POST', '/compras', F, {'nome': 'Caderno', 'qtd': 1, 'categoria': 'Geral', 'frequencia': 'SEM_FREQUENCIA'}, status=[200, 201])
espera('filho vê a foto da família', 'GET', '/familia/foto', F)
for metodo, caminho in [('GET', '/despesas'), ('GET', '/receitas'), ('GET', '/contas'), ('GET', '/mesada'), ('GET', f'/mesada/{M2}'),
                        ('PUT', f'/mesada/{M2}'), ('GET', '/membros'), ('GET', '/metas'), ('POST', '/familia/apagar-dados'),
                        ('POST', '/familia/foto'), ('DELETE', '/familia/foto'), ('PUT', '/familia/me'), ('PUT', '/familia/plano'),
                        ('GET', '/investimentos'), ('GET', '/imposto-renda'), ('DELETE', f'/compras/{CP}'), ('POST', '/receitas')]:
    espera(f'filho NÃO acessa {metodo} {caminho}', metodo, caminho, F, {} if metodo in ('PUT', 'POST') else None, status=403)

# ════════════════ Exclusões com coisas ligadas ════════════════
grupo('Exclusões com dados ligados')
espera('excluir membro que tem despesa', 'DELETE', f'/membros/{M3}', A, status=[200, 204])
desp = espera('despesas depois', 'GET', '/despesas', A) or []
checar('despesa do membro excluído continua (sem membro)', any(x['nome'] == 'Remédio' and not x.get('membroId') for x in desp), '')
espera('excluir filho que tem mesada, login, tarefa e compromisso', 'DELETE', f'/membros/{M2}', A, status=[200, 204])
espera('login do filho excluído não entra mais', 'POST', '/auth/login', corpo={'email': login_filho, 'senha': 'senha-joao'}, status=401)
espera('token antigo do filho excluído não acessa a mesada', 'GET', '/mesada/minha', F, status=[401, 403, 404])

# ════════════════ Apagar todos os dados ════════════════
grupo('Apagar todos os dados')
espera('apagar todos os dados com tudo preenchido', 'POST', '/familia/apagar-dados', A, status=[200, 204])
for caminho in ['/receitas', '/despesas', '/contas', '/compras', '/dispensa', '/tarefas', '/metas', '/membros', '/historico-precos', '/compromissos', '/nfce', '/investimentos']:
    r = espera(f'{caminho} vazio depois de apagar', 'GET', caminho, A)
    checar(f'{caminho} ficou vazio', r == [], str(r)[:120])
r = espera('família continua', 'GET', '/familia/me', A)
checar('família e foto mantidas', r and r.get('nomeFamilia') == 'Família A Silva' and r.get('fotoAtualizadaEm'), str(r)[:120])
espera('login continua funcionando', 'POST', '/auth/login', corpo={'email': email_a, 'senha': 'senha-a-123'})
lst = espera('dados da família B intactos', 'GET', '/membros', B)

# ════════════════ Resultado ════════════════
falhas = [x for x in resultados if not x[2]]
print(f'TOTAL: {len(resultados)} verificações, {len(resultados) - len(falhas)} ok, {len(falhas)} falharam\n')
g = None
for gr, nome, ok, det in resultados:
    if not ok:
        print(f'[{gr}] FALHOU: {nome}\n      {det}')

sys.exit(1 if falhas else 0)
