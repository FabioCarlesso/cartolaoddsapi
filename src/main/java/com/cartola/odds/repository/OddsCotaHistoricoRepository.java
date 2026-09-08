package com.cartola.odds.repository;

import com.cartola.odds.model.OddsCotaHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OddsCotaHistoricoRepository extends JpaRepository<OddsCotaHistorico, Long> {

    /**
     * Serie a partir de um instante, em ordem cronologica. A ordem nao e enfeite: o consumidor
     * detecta a renovacao da cota comparando cada leitura com a anterior, e fora de ordem essa
     * comparacao vira ruido.
     */
    List<OddsCotaHistorico> findByInstanteGreaterThanEqualOrderByInstanteAsc(LocalDateTime desde);
}
