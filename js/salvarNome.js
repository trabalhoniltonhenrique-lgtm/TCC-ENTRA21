export default function initSalvarNome() {
    const botao = document.querySelector('#botao');

    if (!botao) return; 

        botao.addEventListener('click', (event) => {
        event.preventDefault();
        const nome = document.querySelector('#nomeUsuario').value;
    
        if (nome) {
            localStorage.setItem('nomeUsuario', nome);
            window.location.href = 'dashboard.html';
        }
    });
}