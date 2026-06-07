let receitas=JSON.parse(localStorage.getItem('receitas')||'[]');
let despesas=JSON.parse(localStorage.getItem('despesas')||'[]');
let compras=JSON.parse(localStorage.getItem('compras')||'[]');
let tarefas=JSON.parse(localStorage.getItem('tarefas')||'[]');

function salvar(){localStorage.setItem('receitas',JSON.stringify(receitas));localStorage.setItem('despesas',JSON.stringify(despesas));localStorage.setItem('compras',JSON.stringify(compras));localStorage.setItem('tarefas',JSON.stringify(tarefas));}
function atualizarSaldo(){const r=receitas.reduce((a,b)=>a+b.valor,0);const d=despesas.reduce((a,b)=>a+b.valor,0);const el=document.getElementById('saldo');if(el)el.innerText='R$ '+(r-d).toFixed(2);}
function adicionarReceita(){const nome=document.getElementById('receitaNome').value;const valor=parseFloat(document.getElementById('receitaValor').value||0);receitas.push({nome,valor});salvar();renderFinanceiro();}
function adicionarDespesa(){const nome=document.getElementById('despesaNome').value;const valor=parseFloat(document.getElementById('despesaValor').value||0);despesas.push({nome,valor});salvar();renderFinanceiro();}
function excluirReceita(i){if(confirm('Excluir receita?')){receitas.splice(i,1);salvar();renderFinanceiro();}}
function excluirDespesa(i){if(confirm('Excluir despesa?')){despesas.splice(i,1);salvar();renderFinanceiro();}}
function excluirCompra(i){if(confirm('Excluir item?')){compras.splice(i,1);salvar();renderCompras();atualizarDashboard();}}
function excluirTarefa(i){if(confirm('Excluir tarefa?')){tarefas.splice(i,1);salvar();renderTarefas();atualizarDashboard();}}
function renderFinanceiro(){const lr=document.getElementById('listaReceitas');const ld=document.getElementById('listaDespesas');if(lr)lr.innerHTML=receitas.map((x,i)=>`<li>${x.nome} - R$ ${x.valor} <button onclick="excluirReceita(${i})">Excluir</button></li>`).join('');if(ld)ld.innerHTML=despesas.map((x,i)=>`<li>${x.nome} - R$ ${x.valor} <button onclick="excluirDespesa(${i})">Excluir</button></li>`).join('');atualizarSaldo();atualizarDashboard();}
function adicionarCompra(){const item=document.getElementById('compra').value;compras.push(item);salvar();renderCompras();}
function renderCompras(){const l=document.getElementById('listaCompras');if(l)l.innerHTML=compras.map((x,i)=>`<li>${x} <button onclick="excluirCompra(${i})">Excluir</button></li>`).join('');}
function adicionarTarefa(){const item=document.getElementById('tarefa').value;tarefas.push(item);salvar();renderTarefas();}
function renderTarefas(){const l=document.getElementById('listaTarefas');if(l)l.innerHTML=tarefas.map((x,i)=>`<li>${x} <button onclick="excluirTarefa(${i})">Excluir</button></li>`).join('');}
function atualizarDashboard(){const tr=receitas.reduce((a,b)=>a+b.valor,0);const td=despesas.reduce((a,b)=>a+b.valor,0);let e=document.getElementById('saldo');if(e)e.innerText='R$ '+(tr-td).toFixed(2);e=document.getElementById('resumoReceitas');if(e)e.innerText='Receitas: R$ '+tr.toFixed(2);e=document.getElementById('resumoDespesas');if(e)e.innerText='Despesas: R$ '+td.toFixed(2);e=document.getElementById('dashboardCompras');if(e)e.innerHTML=compras.slice(0,5).map(x=>`<li>${x}</li>`).join('');e=document.getElementById('dashboardTarefas');if(e)e.innerHTML=tarefas.slice(0,5).map(x=>`<li>${x}</li>`).join('');}
window.onload=function(){renderFinanceiro();renderCompras();renderTarefas();atualizarSaldo();atualizarDashboard();}
