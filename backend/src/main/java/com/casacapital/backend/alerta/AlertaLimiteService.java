package com.casacapital.backend.alerta;

import com.casacapital.backend.alerta.dto.AlertaLimiteRequest;
import com.casacapital.backend.common.PremiumRequiredException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertaLimiteService {

    private final AlertaLimiteRepository alertaLimiteRepository;
    private final FamiliaRepository familiaRepository;

    public AlertaLimiteService(AlertaLimiteRepository alertaLimiteRepository, FamiliaRepository familiaRepository) {
        this.alertaLimiteRepository = alertaLimiteRepository;
        this.familiaRepository = familiaRepository;
    }

    public List<AlertaLimite> listar(Long familiaId) {
        exigirPremium(familiaId);
        return alertaLimiteRepository.findAllByFamiliaId(familiaId);
    }

    @Transactional
    public AlertaLimite salvar(Long familiaId, AlertaLimiteRequest req) {
        exigirPremium(familiaId);
        AlertaLimite a = alertaLimiteRepository.findByFamiliaIdAndCategoria(familiaId, req.categoria())
                .orElseGet(() -> {
                    AlertaLimite novo = new AlertaLimite();
                    novo.setFamilia(familiaRepository.getReferenceById(familiaId));
                    novo.setCategoria(req.categoria());
                    return novo;
                });
        a.setLimite(req.limite());
        return alertaLimiteRepository.save(a);
    }

    @Transactional
    public void excluir(Long familiaId, String categoria) {
        exigirPremium(familiaId);
        AlertaLimite a = alertaLimiteRepository.findByFamiliaIdAndCategoria(familiaId, categoria)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado."));
        alertaLimiteRepository.delete(a);
    }

    private void exigirPremium(Long familiaId) {
        Familia familia = familiaRepository.findById(familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Família não encontrada."));
        if (!familia.isPremium()) {
            throw new PremiumRequiredException();
        }
    }
}
