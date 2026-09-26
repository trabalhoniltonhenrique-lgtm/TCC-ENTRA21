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
}

function estaAutenticado() {
  return !!getToken();
}

function irParaLogin() {
  limparSessao();
  if (!['index.html', 'cadastro.html', 'home.html', 'esqueci-senha.html', 'redefinir-senha.html', ''].includes(location.pathname.split('/').pop())) {
    location.href = 'index.html';
  }
}

async function apiRequest(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers['Authorization'] = 'Bearer ' + token;

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (erroRede) {
    throw new Error('Não foi possível conectar ao servidor. Verifique sua conexão.');
  }

  if (res.status === 401) {
    irParaLogin();
    throw new Error('Sessão expirada. Faça login novamente.');
  }

  if (!res.ok) {
    let mensagem = 'Erro ao comunicar com o servidor.';
    try {
      const erro = await res.json();
      if (erro && erro.mensagem) mensagem = erro.mensagem;
    } catch { /* corpo sem JSON, mantém mensagem padrão */ }
    const erro = new Error(mensagem);
    erro.status = res.status;
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
