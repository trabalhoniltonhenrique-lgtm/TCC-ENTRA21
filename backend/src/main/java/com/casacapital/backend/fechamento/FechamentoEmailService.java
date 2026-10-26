package com.casacapital.backend.fechamento;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Envia por e-mail, a partir do dia 1, o fechamento do mês anterior às famílias que ativaram a opção.
 * Roda todo dia às 8h e também quando o sistema sobe (o computador pode estar desligado no dia 1);
 * cada mês é enviado uma vez só (tabela fechamento_enviado).
 */
@Service
public class FechamentoEmailService {

    private static final Logger log = LoggerFactory.getLogger(FechamentoEmailService.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final FechamentoService fechamento;
    private final FamiliaRepository familias;
    private final UsuarioRepository usuarios;
    private final FechamentoEnviadoRepository enviados;
    private final JavaMailSender mail;
    private final String remetente;
    private final String baseUrl;

    public FechamentoEmailService(FechamentoService fechamento, FamiliaRepository familias, UsuarioRepository usuarios,
                                  FechamentoEnviadoRepository enviados, JavaMailSender mail,
                                  @Value("${spring.mail.username:}") String remetente,
                                  @Value("${casacapital.frontend-base-url}") String baseUrl) {
        this.fechamento = fechamento;
        this.familias = familias;
        this.usuarios = usuarios;
        this.enviados = enviados;
        this.mail = mail;
        this.remetente = remetente == null ? "" : remetente.trim();
        this.baseUrl = baseUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        enviarPendentes();
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void enviarPendentes() {
        if (remetente.isEmpty()) return; // e-mail não configurado no instalador
        YearMonth mes = YearMonth.now().minusMonths(1);
        for (Familia f : familias.findAll()) {
            if (!f.isFechamentoEmail() || enviados.existsByFamiliaIdAndMes(f.getId(), mes.toString())) continue;
            try {
                enviar(f, mes);
            } catch (RuntimeException e) {
                log.warn("Não foi possível enviar o fechamento de {} da família {}: {}", mes, f.getId(), e.getMessage());
            }
        }
    }

    /** Envia o fechamento do mês para os usuários da família e registra o envio. Devolve quantos e-mails saíram. */
    @Transactional
    public int enviar(Familia familia, YearMonth mes) {
        List<String> destinos = usuarios.findAllByFamiliaIdAndPerfil(familia.getId(), PerfilUsuario.RESPONSAVEL).stream()
                .map(Usuario::getEmail).filter(e -> e != null && e.contains("@")).toList();
        if (destinos.isEmpty()) return 0;
        FechamentoResponse r = fechamento.gerar(familia.getId(), mes);

        SimpleMailMessage msg = new SimpleMailMessage();
        if (!remetente.isEmpty()) msg.setFrom(remetente);
        msg.setTo(destinos.toArray(String[]::new));
        msg.setSubject("CasaCapital — Fechamento de " + nomeMes(mes));
        msg.setText(texto(familia.getNomeFamilia(), r, mes));
        mail.send(msg);

        FechamentoEnviado e = new FechamentoEnviado();
        e.setFamilia(familia);
        e.setMes(mes.toString());
        e.setEnviadoEm(LocalDateTime.now());
        enviados.save(e);
        log.info("Fechamento de {} enviado para a família {} ({} destinatário(s))", mes, familia.getId(), destinos.size());
        return destinos.size();
    }

    String texto(String familia, FechamentoResponse r, YearMonth mes) {
        StringBuilder t = new StringBuilder();
        t.append("Olá, ").append(familia == null ? "família" : familia).append("!\n\n");
        t.append("Este é o fechamento de ").append(nomeMes(mes)).append(".\n\n");
        t.append("ENTRADAS E SAÍDAS\n");
        t.append("  Receitas:  ").append(dinheiro(r.receitas())).append(comparacao(r.receitas(), r.receitasAnterior())).append('\n');
        t.append("  Despesas:  ").append(dinheiro(r.despesas())).append(comparacao(r.despesas(), r.despesasAnterior())).append('\n');
        t.append("  Saldo do mês: ").append(dinheiro(r.saldo())).append("\n\n");

        if (!r.categorias().isEmpty()) {
            t.append("ONDE O DINHEIRO FOI\n");
            r.categorias().stream().limit(6).forEach(c -> t.append("  ").append(c.categoria()).append(": ")
                    .append(dinheiro(c.valor()))
                    .append(c.variacaoPct() == null ? "" : " (" + sinal(c.variacaoPct()) + "% vs. mês anterior)").append('\n'));
            t.append('\n');
        }
        List<FechamentoResponse.Orcamento> estouradas = r.orcamento().stream().filter(FechamentoResponse.Orcamento::estourou).toList();
        if (!r.orcamento().isEmpty()) {
            t.append("ORÇAMENTO\n  ").append(r.orcamento().size() - estouradas.size()).append(" de ").append(r.orcamento().size())
                    .append(" categorias dentro do limite.\n");
            estouradas.forEach(o -> t.append("  Passou do limite: ").append(o.categoria()).append(" — ").append(dinheiro(o.gasto()))
                    .append(" de ").append(dinheiro(o.limite())).append('\n'));
            t.append('\n');
        }
        if (!r.metas().isEmpty()) {
            t.append("METAS\n");
            r.metas().forEach(m -> t.append("  ").append(m.nome()).append(": ").append(m.pct()).append("% (")
                    .append(dinheiro(m.totalPoupado())).append(" de ").append(dinheiro(m.valorAlvo())).append(')')
                    .append(m.aportadoNoMes().signum() > 0 ? " — guardou " + dinheiro(m.aportadoNoMes()) + " no mês" : "").append('\n'));
            t.append('\n');
        }
        if (r.assinaturas().quantidade() > 0) {
            t.append("ASSINATURAS\n  ").append(r.assinaturas().quantidade()).append(" cobrada(s), total ")
                    .append(dinheiro(r.assinaturas().total())).append('\n');
            r.assinaturas().itens().stream().filter(a -> a.aumentoPct() != null).forEach(a -> t.append("  ")
                    .append(a.nome()).append(" aumentou ").append(a.aumentoPct().toPlainString().replace('.', ',')).append("%\n"));
            t.append('\n');
        }
        if (r.patrimonio() != null) {
            t.append("PATRIMÔNIO\n  ").append(dinheiro(r.patrimonio().total()));
            if (r.patrimonio().variacao() != null) {
                t.append(" (").append(r.patrimonio().variacao().signum() >= 0 ? "+" : "−")
                        .append(dinheiro(r.patrimonio().variacao().abs())).append(" no mês)");
            }
            t.append("\n\n");
        }
        t.append("Veja os detalhes em ").append(baseUrl).append("/fechamento.html?mes=").append(mes).append("\n\n");
        t.append("— CasaCapital\n(Para não receber mais, desligue a opção na página Fechamento do mês.)\n");
        return t.toString();
    }

    private static String comparacao(BigDecimal atual, BigDecimal anterior) {
        BigDecimal v = FechamentoService.variacao(atual, anterior);
        return v == null ? "" : "  (" + sinal(v) + "% vs. mês anterior)";
    }

    private static String sinal(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + v.toPlainString().replace('.', ',');
    }

    static String dinheiro(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return (v.signum() < 0 ? "-R$ " : "R$ ") + f.format(v.abs());
    }

    static String nomeMes(YearMonth mes) {
        String s = mes.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR));
        return s.substring(0, 1).toUpperCase(PT_BR) + s.substring(1);
    }
}
