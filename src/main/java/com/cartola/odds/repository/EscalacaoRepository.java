package com.cartola.odds.repository;

import com.cartola.odds.model.EscalacaoRodada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EscalacaoRepository extends JpaRepository<EscalacaoRodada, Long> {

    List<EscalacaoRodada> findByRodadaId(Integer rodadaId);

    boolean existsByRodadaId(Integer rodadaId);

    List<EscalacaoRodada> findAllByOrderByRodadaIdAscIdAsc();
}
