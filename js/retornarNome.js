export default function initRetornarNome() {
    const nome = localStorage.getItem('nomeUsuario');
    if (nome) {
        document.querySelector('.exibeNome').textContent = nome;
    }
}