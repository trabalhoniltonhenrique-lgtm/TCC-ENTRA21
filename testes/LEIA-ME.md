# Testes do CasaCapital

## Testes automáticos do servidor (rápidos)

Dentro de `backend/`:

```
./mvnw test -Dtest='!BackendApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```

## Teste completo (antes de cada pacote novo)

Roda o sistema de verdade num **ambiente separado** (MySQL temporário na porta 3307 e sistema na 8090) e apaga tudo
no final. **Não toca no sistema nem no banco da casa.** Precisa do pacote gerado (`dist/`).

```
python testes/teste_completo.py
```

- `teste_api.py` — 230 verificações: uso normal de cada módulo, separação entre famílias, limites do login do filho,
  dados inválidos, pagar duas vezes, exclusões com dados ligados e "Apagar todos os dados".

Com os **dados reais**, numa cópia do banco feita a partir de um backup (a cópia é apagada ao final):

```
python testes/teste_completo.py --banco C:\Git\casacapital-backups\AAAA-MM-DD_HHMM\banco-casacapital.sql
```

- `teste_dados_reais.py` — entra como cada login da cópia e abre todas as listas e relatórios (só leitura). Mostra
  apenas os códigos de resposta, nunca valores ou nomes.

Para testar as telas no navegador: `--manter` deixa ligado em http://localhost:8090; depois `--limpar` desliga e apaga.

O resultado termina com **TUDO CERTO** ou **HÁ FALHAS**. Qualquer erro interno (500) no log do sistema conta como falha.
