package com.casacapital.backend.orcamento;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.orcamento.dto.OrcamentoItemRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class OrcamentoMensalService {

    private static final Pattern MES_ANO = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final OrcamentoMensalRepository orcamentoRepository;
    private final FamiliaRepository familiaRepository;

    public OrcamentoMensalService(OrcamentoMensalRepository orcamentoRepository, FamiliaRepository familiaRepository) {
        this.orcamentoRepository = orcamentoRepository;
        this.familiaRepository = familiaRepository;
    }

    public List<OrcamentoMensal> listar(Long familiaId, String mesAno) {
        validarMesAno(mesAno);
        return orcamentoRepository.findAllByFamiliaIdAndMesAno(familiaId, mesAno);
    }

    @Transactional
    public OrcamentoMensal definir(Long familiaId, String mesAno, OrcamentoItemRequest req) {
        validarMesAno(mesAno);
        OrcamentoMensal o = orcamentoRepository.findByFamiliaIdAndMesAnoAndCategoria(familiaId, mesAno, req.categoria())
                .orElseGet(() -> {
                    OrcamentoMensal novo = new OrcamentoMensal();
                    novo.setFamilia(familiaRepository.getReferenceById(familiaId));
                    novo.setMesAno(mesAno);
                    novo.setCategoria(req.categoria());
                    return novo;
                });
        o.setLimite(req.limite());
        return orcamentoRepository.save(o);
    }

    @Transactional
    public void remover(Long familiaId, String mesAno, String categoria) {
        validarMesAno(mesAno);
        OrcamentoMensal o = orcamentoRepository.findByFamiliaIdAndMesAnoAndCategoria(familiaId, mesAno, categoria)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado."));
        orcamentoRepository.delete(o);
    }

    /** Copia os orçamentos do mês anterior para o mês informado, se o mês informado ainda estiver vazio. */
    @Transactional
    public List<OrcamentoMensal> copiarMesAnterior(Long familiaId, String mesAno) {
        validarMesAno(mesAno);
        String mesAnterior = YearMonth.parse(mesAno, FORMATO).minusMonths(1).format(FORMATO);
        List<OrcamentoMensal> doMesAnterior = orcamentoRepository.findAllByFamiliaIdAndMesAno(familiaId, mesAnterior);
        if (doMesAnterior.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SEM_ORCAMENTO_ANTERIOR",
                    "Não há orçamento definido no mês anterior.");
        }

        List<OrcamentoMensal> copiados = doMesAnterior.stream().map(origem -> {
            OrcamentoMensal copia = orcamentoRepository
                    .findByFamiliaIdAndMesAnoAndCategoria(familiaId, mesAno, origem.getCategoria())
                    .orElseGet(() -> {
                        OrcamentoMensal novo = new OrcamentoMensal();
                        novo.setFamilia(familiaRepository.getReferenceById(familiaId));
                        novo.setMesAno(mesAno);
                        novo.setCategoria(origem.getCategoria());
                        return novo;
                    });
            copia.setLimite(origem.getLimite());
            return orcamentoRepository.save(copia);
        }).toList();

        return copiados;
    }

    private void validarMesAno(String mesAno) {
        if (mesAno == null || !MES_ANO.matcher(mesAno).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MES_ANO_INVALIDO", "Formato esperado: YYYY-MM.");
        }
    }
}
