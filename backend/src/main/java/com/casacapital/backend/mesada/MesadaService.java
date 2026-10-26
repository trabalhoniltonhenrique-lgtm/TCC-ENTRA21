package com.casacapital.backend.mesada;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.mesada.dto.AcessoDependenteRequest;
import com.casacapital.backend.mesada.dto.GastoMesadaRequest;
import com.casacapital.backend.mesada.dto.MesadaConfigRequest;
import com.casacapital.backend.mesada.dto.MesadaResponse;
import com.casacapital.backend.mesada.dto.MovimentoMesadaRequest;
import com.casacapital.backend.mesada.dto.MovimentoMesadaResponse;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mesada dos filhos: crédito automático todo mês, extras e gastos, objetivo e o login do filho
 * (perfil DEPENDENTE), que só enxerga a própria mesada, as próprias tarefas e a lista de compras.
 */
@Service
public class MesadaService {

    private static final Logger log = LoggerFactory.getLogger(MesadaService.class);
    private static final int LIMITE_MOVIMENTOS = 100;

    private final MesadaRepository mesadas;
    private final MesadaMovimentoRepository movimentos;
    private final MembroFamiliaRepository membros;
    private final FamiliaRepository familias;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public MesadaService(MesadaRepository mesadas, MesadaMovimentoRepository movimentos,
                         MembroFamiliaRepository membros, FamiliaRepository familias,
                         UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.mesadas = mesadas;
        this.movimentos = movimentos;
        this.membros = membros;
        this.familias = familias;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Leitura ─────────────────────────────────────────────────────────

    /** Todos os membros da família, com a mesada de quem tem (para a tela dos pais). */
    @Transactional
    public List<MesadaResponse> listar(Long familiaId) {
        Map<Long, Mesada> porMembro = mesadas.findAllByFamiliaId(familiaId).stream()
                .collect(Collectors.toMap(m -> m.getMembro().getId(), m -> m));
        Map<Long, String> logins = loginsPorMembro(familiaId);
        LocalDate hoje = LocalDate.now();
        return membros.findAllByFamiliaIdOrderByNomeAsc(familiaId).stream()
                .map(mb -> montar(mb, porMembro.get(mb.getId()), logins.get(mb.getId()), hoje))
                .toList();
    }

    @Transactional
    public MesadaResponse detalhe(Long familiaId, Long membroId) {
        MembroFamilia membro = buscarMembro(familiaId, membroId);
        Mesada mesada = mesadas.findByMembroIdAndFamiliaId(membroId, familiaId).orElse(null);
        return montar(membro, mesada, loginsPorMembro(familiaId).get(membroId), LocalDate.now());
    }

    // ── Configuração (pais) ─────────────────────────────────────────────

    @Transactional
    public MesadaResponse configurar(Long familiaId, Long membroId, MesadaConfigRequest req) {
        MembroFamilia membro = buscarMembro(familiaId, membroId);
        LocalDate hoje = LocalDate.now();
        Mesada m = mesadas.findByMembroIdAndFamiliaId(membroId, familiaId).orElseGet(() -> {
            Mesada nova = new Mesada();
            nova.setFamilia(familias.getReferenceById(familiaId));
            nova.setMembro(membro);
            nova.setInicio(hoje);
            return nova;
        });
        boolean ativa = req.ativa() == null || req.ativa();
        // ao reativar, os meses parados não viram crédito
        if (ativa && m.getId() != null && !m.isAtiva()) m.setInicio(hoje);
        m.setValor(req.valor());
        m.setDiaPagamento(req.diaPagamento());
        m.setAtiva(ativa);
        String meta = req.metaNome() != null ? req.metaNome().trim() : "";
        m.setMetaNome(meta.isEmpty() ? null : meta);
        m.setMetaValor(meta.isEmpty() ? null : req.metaValor());
        mesadas.save(m);
        return detalhe(familiaId, membroId);
    }

    @Transactional
    public void removerConfiguracao(Long familiaId, Long membroId) {
        mesadas.findByMembroIdAndFamiliaId(membroId, familiaId).ifPresent(mesadas::delete);
    }

    @Transactional
    public MesadaResponse lancar(Long familiaId, Long membroId, MovimentoMesadaRequest req, String autor) {
        if (req.tipo() == TipoMovimentoMesada.MESADA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIPO_INVALIDO",
                    "A mesada do mês é lançada sozinha; use Extra ou Gasto.");
        }
        MembroFamilia membro = buscarMembro(familiaId, membroId);
        salvarMovimento(familiaId, membro, req.tipo(), req.valor(), req.descricao().trim(),
                req.data() != null ? req.data() : LocalDate.now(), null, autor);
        return detalhe(familiaId, membroId);
    }

    @Transactional
    public MesadaResponse excluirMovimento(Long familiaId, Long membroId, Long movimentoId) {
        MesadaMovimento mov = movimentos.findByIdAndMembroIdAndFamiliaId(movimentoId, membroId, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
        if (mov.getTipo() == TipoMovimentoMesada.MESADA) {
            // seria lançada de novo no próximo cálculo; o acerto se faz com um gasto ou extra
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESADA_AUTOMATICA",
                    "A mesada do mês não pode ser apagada. Para acertar o saldo, lance um Gasto ou um Extra.");
        }
        movimentos.delete(mov);
        return detalhe(familiaId, membroId);
    }

    // ── Login do filho (pais) ───────────────────────────────────────────

    @Transactional
    public MesadaResponse definirAcesso(Long familiaId, Long membroId, AcessoDependenteRequest req) {
        MembroFamilia membro = buscarMembro(familiaId, membroId);
        String login = req.login().trim().toLowerCase();
        Usuario u = usuarios.findByMembroIdAndFamiliaId(membroId, familiaId).orElse(null);

        usuarios.findByEmailIgnoreCase(login)
                .filter(outro -> u == null || !outro.getId().equals(u.getId()))
                .ifPresent(outro -> {
                    throw new ApiException(HttpStatus.CONFLICT, "LOGIN_EM_USO", "Esse nome de usuário já está em uso. Escolha outro.");
                });

        boolean temSenha = req.senha() != null && !req.senha().isBlank();
        if (u == null) {
            if (!temSenha) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_OBRIGATORIA", "Defina uma senha para o primeiro acesso.");
            }
            Usuario novo = new Usuario(familias.getReferenceById(familiaId), membro.getNome(), login,
                    passwordEncoder.encode(req.senha()));
            novo.setPerfil(PerfilUsuario.DEPENDENTE);
            novo.setMembro(membro);
            usuarios.save(novo);
        } else {
            u.setEmail(login);
            u.setNome(membro.getNome());
            if (temSenha) u.setSenhaHash(passwordEncoder.encode(req.senha()));
        }
        return detalhe(familiaId, membroId);
    }

    @Transactional
    public MesadaResponse removerAcesso(Long familiaId, Long membroId) {
        buscarMembro(familiaId, membroId);
        usuarios.findByMembroIdAndFamiliaId(membroId, familiaId).ifPresent(usuarios::delete);
        return detalhe(familiaId, membroId);
    }

    // ── Área do filho ───────────────────────────────────────────────────

    @Transactional
    public MesadaResponse gastoDoFilho(Long familiaId, Long membroId, GastoMesadaRequest req) {
        MembroFamilia membro = buscarMembro(familiaId, membroId);
        MesadaResponse atual = detalhe(familiaId, membroId);
        if (req.valor().compareTo(atual.saldo()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SALDO_INSUFICIENTE",
                    "O gasto é maior que o saldo da mesada. Fale com seus pais.");
        }
        salvarMovimento(familiaId, membro, TipoMovimentoMesada.GASTO, req.valor(), req.descricao().trim(),
                LocalDate.now(), null, membro.getNome());
        return detalhe(familiaId, membroId);
    }

    // ── Crédito automático ──────────────────────────────────────────────

    /** Todo dia, logo depois da meia-noite, credita as mesadas do dia (também roda ao abrir a tela). */
    @Scheduled(cron = "0 5 0 * * *")
    public void creditarTodas() {
        LocalDate hoje = LocalDate.now();
        for (Mesada m : mesadas.findAllByAtivaTrue()) {
            try {
                garantirCreditos(m, hoje);
            } catch (RuntimeException e) {
                log.warn("Não foi possível creditar a mesada {}: {}", m.getId(), e.getMessage());
            }
        }
    }

    /** Lança a mesada de cada mês, do início até hoje, cujo dia de pagamento já chegou. */
    void garantirCreditos(Mesada m, LocalDate hoje) {
        if (!m.isAtiva()) return;
        Long membroId = m.getMembro().getId();
        for (YearMonth mes = YearMonth.from(m.getInicio()); !mes.isAfter(YearMonth.from(hoje)); mes = mes.plusMonths(1)) {
            LocalDate dia = diaDoPagamento(mes, m.getDiaPagamento());
            if (dia.isBefore(m.getInicio()) || dia.isAfter(hoje)) continue;
            if (movimentos.existsByMembroIdAndReferencia(membroId, mes.toString())) continue;
            try {
                salvarMovimento(m.getFamilia().getId(), m.getMembro(), TipoMovimentoMesada.MESADA, m.getValor(),
                        "Mesada de " + nomeMes(mes), dia, mes.toString(), null);
            } catch (DataIntegrityViolationException e) {
                // outra requisição lançou a mesma mesada ao mesmo tempo: a do banco vale
            }
        }
    }

    static LocalDate diaDoPagamento(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    static LocalDate proximoPagamento(Mesada m, LocalDate hoje) {
        YearMonth mes = YearMonth.from(hoje);
        LocalDate dia = diaDoPagamento(mes, m.getDiaPagamento());
        return dia.isAfter(hoje) ? dia : diaDoPagamento(mes.plusMonths(1), m.getDiaPagamento());
    }

    // ── Auxiliares ──────────────────────────────────────────────────────

    private MesadaResponse montar(MembroFamilia membro, Mesada mesada, String login, LocalDate hoje) {
        if (mesada != null) garantirCreditos(mesada, hoje);
        List<MesadaMovimento> lista = movimentos.findAllByMembroIdAndFamiliaIdOrderByDataDescIdDesc(
                membro.getId(), membro.getFamilia().getId());

        BigDecimal saldo = BigDecimal.ZERO, recebido = BigDecimal.ZERO, gasto = BigDecimal.ZERO;
        YearMonth mesAtual = YearMonth.from(hoje);
        for (MesadaMovimento mov : lista) {
            boolean noMes = YearMonth.from(mov.getData()).equals(mesAtual);
            if (mov.getTipo().entrada()) {
                saldo = saldo.add(mov.getValor());
                if (noMes) recebido = recebido.add(mov.getValor());
            } else {
                saldo = saldo.subtract(mov.getValor());
                if (noMes) gasto = gasto.add(mov.getValor());
            }
        }

        Integer metaPct = null;
        if (mesada != null && mesada.getMetaValor() != null && mesada.getMetaValor().signum() > 0) {
            metaPct = (int) Math.max(0, Math.min(100,
                    saldo.multiply(BigDecimal.valueOf(100)).divide(mesada.getMetaValor(), 0, java.math.RoundingMode.DOWN).longValue()));
        }

        return new MesadaResponse(
                membro.getId(), membro.getNome(), membro.getCor(),
                mesada != null,
                mesada != null ? mesada.getValor() : null,
                mesada != null ? mesada.getDiaPagamento() : null,
                mesada != null && mesada.isAtiva(),
                mesada != null && mesada.isAtiva() ? proximoPagamento(mesada, hoje) : null,
                mesada != null ? mesada.getMetaNome() : null,
                mesada != null ? mesada.getMetaValor() : null,
                metaPct, saldo, recebido, gasto, login,
                lista.stream().limit(LIMITE_MOVIMENTOS).map(MovimentoMesadaResponse::from).toList());
    }

    private void salvarMovimento(Long familiaId, MembroFamilia membro, TipoMovimentoMesada tipo, BigDecimal valor,
                                 String descricao, LocalDate data, String referencia, String autor) {
        MesadaMovimento mov = new MesadaMovimento();
        mov.setFamilia(familias.getReferenceById(familiaId));
        mov.setMembro(membro);
        mov.setTipo(tipo);
        mov.setValor(valor);
        mov.setDescricao(descricao);
        mov.setData(data);
        mov.setReferencia(referencia);
        mov.setCriadoPor(autor);
        movimentos.saveAndFlush(mov);
    }

    private Map<Long, String> loginsPorMembro(Long familiaId) {
        return usuarios.findAllByFamiliaIdAndPerfil(familiaId, PerfilUsuario.DEPENDENTE).stream()
                .filter(u -> u.getMembro() != null)
                .collect(Collectors.toMap(u -> u.getMembro().getId(), Usuario::getEmail, (a, b) -> a));
    }

    private MembroFamilia buscarMembro(Long familiaId, Long membroId) {
        return membros.findByIdAndFamiliaId(membroId, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
    }

    private static String nomeMes(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.of("pt", "BR"));
        return nome + "/" + mes.getYear();
    }
}
