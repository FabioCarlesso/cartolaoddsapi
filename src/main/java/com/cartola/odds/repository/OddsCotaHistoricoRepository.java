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
     *
     * <p>O {@code id} desempata. Duas leituras no mesmo microssegundo sao praticamente
     * impossiveis — elas nascem de chamadas ao provedor, espacadas por minutos —, mas sem o
     * desempate a ordem entre elas seria escolha do banco, e ordem e justamente o contrato
     * desta consulta.
     *
     * <p>Ordenar pelo <em>instante da leitura</em>, e nao pela ordem de insercao, tambem e o que
     * mantem a serie correta se um dia houver mais de uma instancia gravando: quem chegou ao
     * banco primeiro nao importa, o que vale e quando a cota foi lida.
     */
    List<OddsCotaHistorico> findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(LocalDateTime desde);
}
