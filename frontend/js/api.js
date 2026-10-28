// ── CasaCapital — cliente da API ──
// Substitui o antigo padrão de localStorage.getItem/setItem por chamadas HTTP autenticadas.

const API_BASE = '/api';
const CHAVE_TOKEN = 'casacapital_token';

function getToken() {
  return localStorage.getItem(CHAVE_TOKEN);
}

function setToken(token) {
  localStorage.setItem(CHAVE_TOKEN, token);
}

function limparSessao() {
  localStorage.removeItem(CHAVE_TOKEN);
  localStorage.removeItem('casacapital_cor'); // a próxima família a entrar não herda as cores desta
  localStorage.removeItem(CHAVE_FOTO_FAMILIA); // nem a foto
}

// ── Foto da família ──
// A imagem exige login, então não dá para usar <img src="/api/..."> direto: baixa com o token e guarda uma cópia
// (data URL) no navegador, trocada só quando familia.fotoAtualizadaEm muda.
const CHAVE_FOTO_FAMILIA = 'casacapital_foto_familia';

async function fotoDaFamilia(familia) {
  if (!familia || !familia.fotoAtualizadaEm) {
    try { localStorage.removeItem(CHAVE_FOTO_FAMILIA); } catch { /* armazenamento bloqueado */ }
    return null;
  }
  const versao = familia.id + '@' + familia.fotoAtualizadaEm;
  try {
    const guardada = JSON.parse(localStorage.getItem(CHAVE_FOTO_FAMILIA) || 'null');
    if (guardada && guardada.versao === versao) return guardada.dataUrl;
  } catch { /* cópia estragada ou armazenamento bloqueado: baixa de novo */ }

  try {
    const res = await fetch(API_BASE + '/familia/foto', { headers: { 'Authorization': 'Bearer ' + getToken() } });
    if (!res.ok) return null;
    const blob = await res.blob();
    const dataUrl = await new Promise((ok, falha) => {
      const leitor = new FileReader();
      leitor.onload = () => ok(leitor.result);
      leitor.onerror = falha;
      leitor.readAsDataURL(blob);
    });
    try { localStorage.setItem(CHAVE_FOTO_FAMILIA, JSON.stringify({ versao, dataUrl })); } catch { /* sem espaço: só não guarda */ }
    return dataUrl;
  } catch {
    return null;
  }
}

function estaAutenticado() {
  return !!getToken();
}

// Perfil gravado no token pelo servidor: RESPONSAVEL (pais) ou DEPENDENTE (filho).
// Só decide a tela a mostrar; quem barra o acesso de verdade é o servidor.
function perfilDoToken() {
  try {
    const payload = getToken().split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).perfil || 'RESPONSAVEL';
  } catch { return 'RESPONSAVEL'; }
}

function ehDependente() {
  return estaAutenticado() && perfilDoToken() === 'DEPENDENTE';
}

// Tela inicial depois do login: o filho vai para a área dele
function paginaInicial() {
  return ehDependente() ? 'minha-area.html' : 'dashboard.html';
}

function irParaLogin() {
  limparSessao();
  if (!['index.html', 'cadastro.html', 'home.html', 'esqueci-senha.html', 'redefinir-senha.html', ''].includes(location.pathname.split('/').pop())) {
    location.href = 'index.html';
  }
}

async function apiRequest(method, path, body) {
  const ehFormulario = body instanceof FormData; // upload de arquivo: o navegador monta o Content-Type
  const headers = ehFormulario ? {} : { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = 'Bearer ' + token;

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : ehFormulario ? body : JSON.stringify(body),
    });
  } catch (erroRede) {
    throw new Error('Não foi possível conectar ao servidor. Verifique sua conexão.');
  }

  if (res.status === 401) {
    irParaLogin();
    throw new Error('Sessão expirada. Faça login novamente.');
  }

  if (res.status === 403 && ehDependente() && location.pathname.split('/').pop() !== 'minha-area.html') {
    location.href = 'minha-area.html';
    throw new Error('Esta parte do sistema é dos responsáveis.');
  }

  if (!res.ok) {
    let mensagem = 'Erro ao comunicar com o servidor.';
    let codigo = null;
    try {
      const erro = await res.json();
      if (erro && erro.mensagem) mensagem = erro.mensagem;
      if (erro && erro.codigo) codigo = erro.codigo;
    } catch { /* corpo sem JSON, mantém mensagem padrão */ }
    const erro = new Error(mensagem);
    erro.status = res.status;
    erro.codigo = codigo;
    throw erro;
  }

  if (res.status === 204) return null;
  const texto = await res.text();
  return texto ? JSON.parse(texto) : null;
}

const api = {
  get:  (path)       => apiRequest('GET', path),
  post: (path, body)  => apiRequest('POST', path, body),
  put:  (path, body)  => apiRequest('PUT', path, body),
  del:  (path)        => apiRequest('DELETE', path),
};

// ── Mapeamento de enums entre o front-end (minúsculo, usado no HTML/CSS) e a API (MAIÚSCULO) ──
const FREQ_API   = { 'diaria':'DIARIA', 'semanal':'SEMANAL', 'quinzenal':'QUINZENAL', 'mensal':'MENSAL', 'sem-frequencia':'SEM_FREQUENCIA' };
const FREQ_UI    = Object.fromEntries(Object.entries(FREQ_API).map(([k, v]) => [v, k]));
const RECORR_API = { 'unica':'UNICA', 'mensal':'MENSAL', 'anual':'ANUAL', 'parcelada':'PARCELADA' };
const RECORR_UI  = Object.fromEntries(Object.entries(RECORR_API).map(([k, v]) => [v, k]));
