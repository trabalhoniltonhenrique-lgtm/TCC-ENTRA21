package com.casacapital.backend.familia;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.dto.FamiliaUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamiliaService {

    private final FamiliaRepository familiaRepository;

    public FamiliaService(FamiliaRepository familiaRepository) {
        this.familiaRepository = familiaRepository;
    }

    public Familia buscar(Long familiaId) {
        return familiaRepository.findById(familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Família não encontrada."));
    }

    @Transactional
    public Familia atualizar(Long familiaId, FamiliaUpdateRequest req) {
        Familia familia = buscar(familiaId);
        familia.setNomeFamilia(req.nomeFamilia());
        familia.setCidade(req.cidade());
        familia.setResponsavel(req.responsavel());
        if (req.moeda() != null) familia.setMoeda(req.moeda());
        if (req.separadorDecimal() != null) familia.setSeparadorDecimal(req.separadorDecimal());
        if (req.diaFechamento() != null) familia.setDiaFechamento(req.diaFechamento());
        if (req.mostrarSaldo() != null) familia.setMostrarSaldo(req.mostrarSaldo());
        if (req.alertaContas() != null) familia.setAlertaContas(req.alertaContas());
        if (req.confirmarExclusao() != null) familia.setConfirmarExclusao(req.confirmarExclusao());
        if (req.agruparCat() != null) familia.setAgruparCat(req.agruparCat());
        return familia;
    }

    @Transactional
    public Familia alterarPlano(Long familiaId, PlanoFamilia novoPlano) {
        Familia familia = buscar(familiaId);
        familia.setPlano(novoPlano);
        return familia;
    }
}
