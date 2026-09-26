package com.casacapital.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;

    public MailService(JavaMailSender mailSender,
                        @Value("${casacapital.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void enviarEmailRedefinicaoSenha(String destinatario, String nome, String token) {
        String link = frontendBaseUrl + "/redefinir-senha.html?token=" + token;

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(destinatario);
        mensagem.setSubject("CasaCapital — Redefinição de senha");
        mensagem.setText(
                "Olá " + nome + ",\n\n"
                + "Recebemos uma solicitação para redefinir sua senha no CasaCapital.\n\n"
                + "Clique no link abaixo para criar uma nova senha (válido por 30 minutos):\n"
                + link + "\n\n"
                + "Se você não solicitou essa alteração, ignore este e-mail — sua senha atual continua válida."
        );
        mailSender.send(mensagem);
    }
}
