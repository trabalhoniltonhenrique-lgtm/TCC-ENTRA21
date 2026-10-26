package com.casacapital.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    java.util.List<Usuario> findAllByFamiliaId(Long familiaId);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<Usuario> findAllByFamiliaIdAndPerfil(Long familiaId, PerfilUsuario perfil);

    Optional<Usuario> findByMembroIdAndFamiliaId(Long membroId, Long familiaId);
}
