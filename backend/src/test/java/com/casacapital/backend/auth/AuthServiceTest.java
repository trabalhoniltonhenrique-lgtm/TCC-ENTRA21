package com.casacapital.backend.auth;

import com.casacapital.backend.auth.dto.AuthResponse;
import com.casacapital.backend.auth.dto.LoginRequest;
import com.casacapital.backend.auth.dto.RegistrarRequest;
import com.casacapital.backend.common.BadCredentialsException;
import com.casacapital.backend.common.EmailJaCadastradoException;
import com.casacapital.backend.common.TokenInvalidoException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.familia.PlanoFamilia;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.security.JwtService;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cadastro, login (pais e filho), esqueci a senha e redefinir senha. */
class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // custo baixo: só para o teste ser rápido
    private UsuarioRepository usuarios;
    private FamiliaRepository familias;
    private JwtService jwt;
    private PasswordResetTokenRepository tokens;
    private MailService mail;
    private AuthService service;
    private Usuario pai, filho;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        familias = mock(FamiliaRepository.class);
        jwt = mock(JwtService.class);
        tokens = mock(PasswordResetTokenRepository.class);
        mail = mock(MailService.class);
        service = new AuthService(familias, usuarios, encoder, jwt, tokens, mail);

        Familia familia = new Familia("Família Moraes");
        familia.setId(7L);
        pai = new Usuario(familia, "Nilton", "pai@casa.com", encoder.encode("senha-do-pai"));
        pai.setId(1L);
        MembroFamilia membro = new MembroFamilia();
        membro.setId(20L);
        filho = new Usuario(familia, "João", "joao", encoder.encode("senha-do-joao"));
        filho.setId(30L);
        filho.setPerfil(PerfilUsuario.DEPENDENTE);
        filho.setMembro(membro);
        when(usuarios.findByEmailIgnoreCase("pai@casa.com")).thenReturn(Optional.of(pai));
        when(usuarios.findByEmailIgnoreCase("joao")).thenReturn(Optional.of(filho));
        when(usuarios.findByEmailIgnoreCase("ninguem@casa.com")).thenReturn(Optional.empty());
        when(jwt.gerarToken(any(), any(), any(), any(), any(), any())).thenReturn("token");
        when(jwt.gerarToken(any(), any(), any(), any())).thenReturn("token");
    }

    @Test
    void paiEntraComOEmailDeQualquerJeito() {
        when(usuarios.findByEmailIgnoreCase("Pai@Casa.com")).thenReturn(Optional.of(pai));

        AuthResponse resp = service.login(new LoginRequest("  Pai@Casa.com ", "senha-do-pai"));

        assertThat(resp.token()).isEqualTo("token");
        verify(jwt).gerarToken(1L, 7L, "pai@casa.com", PlanoFamilia.ESSENCIAL, PerfilUsuario.RESPONSAVEL, null);
    }

    @Test
    void filhoEntraComOUsuarioEOTokenDizQueEleEhDependente() {
        service.login(new LoginRequest("joao", "senha-do-joao"));

        verify(jwt).gerarToken(30L, 7L, "joao", PlanoFamilia.ESSENCIAL, PerfilUsuario.DEPENDENTE, 20L);
    }

    @Test
    void senhaErradaEUsuarioInexistenteDaoAMesmaResposta() {
        // mesma mensagem nos dois casos: não revela quais logins existem
        assertThatThrownBy(() -> service.login(new LoginRequest("pai@casa.com", "errada")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@casa.com", "qualquer")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwt, never()).gerarToken(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cadastroGuardaOEmailEmMinusculasEASenhaCriptografada() {
        when(usuarios.existsByEmailIgnoreCase("Mae@Casa.com")).thenReturn(false);
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        service.registrar(new RegistrarRequest("Família Moraes", "Ana", "Mae@Casa.com", "senha123"));

        verify(usuarios).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getEmail().equals("mae@casa.com") && !u.getSenhaHash().equals("senha123")
                        && encoder.matches("senha123", u.getSenhaHash())));
    }

    @Test
    void cadastroComEmailRepetidoEhRecusado() {
        when(usuarios.existsByEmailIgnoreCase("pai@casa.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(new RegistrarRequest("Outra", "Outro", "pai@casa.com", "senha123")))
                .isInstanceOf(EmailJaCadastradoException.class);
        verify(familias, never()).save(any());
    }

    @Test
    void esqueciASenhaSoEnviaEmailParaOsPais() {
        service.esqueciSenha("pai@casa.com");
        service.esqueciSenha("joao");             // a senha do filho é trocada pelos pais, na tela Mesada
        service.esqueciSenha("ninguem@casa.com"); // não existe: responde igual, sem enviar

        verify(mail).enviarEmailRedefinicaoSenha(eq("pai@casa.com"), eq("Nilton"), anyString());
        verify(mail, never()).enviarEmailRedefinicaoSenha(eq("joao"), any(), any());
    }

    @Test
    void falhaNoEnvioDoEmailViraAvisoClaro() {
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("Gmail fora do ar"))
                .when(mail).enviarEmailRedefinicaoSenha(any(), any(), any());

        assertThatThrownBy(() -> service.esqueciSenha("pai@casa.com"))
                .isInstanceOf(com.casacapital.backend.common.ApiException.class)
                .extracting("code").isEqualTo("EMAIL_INDISPONIVEL");
    }

    @Test
    void redefinirTrocaASenhaEInvalidaOLink() {
        PasswordResetToken token = new PasswordResetToken(pai, "abc", Instant.now().plusSeconds(600));
        when(tokens.findByToken("abc")).thenReturn(Optional.of(token));

        service.redefinirSenha("abc", "nova-senha");

        assertThat(encoder.matches("nova-senha", pai.getSenhaHash())).isTrue();
        assertThat(token.isValido()).isFalse(); // o mesmo link não serve duas vezes
    }

    @Test
    void linkVencidoOuJaUsadoNaoTrocaASenha() {
        String hashAntes = pai.getSenhaHash();
        when(tokens.findByToken("vencido")).thenReturn(Optional.of(
                new PasswordResetToken(pai, "vencido", Instant.now().minusSeconds(1))));

        assertThatThrownBy(() -> service.redefinirSenha("vencido", "nova-senha")).isInstanceOf(TokenInvalidoException.class);
        assertThatThrownBy(() -> service.redefinirSenha("inexistente", "nova-senha")).isInstanceOf(TokenInvalidoException.class);
        assertThat(pai.getSenhaHash()).isEqualTo(hashAntes);
    }
}
