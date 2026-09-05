package com.cartola.odds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ultimo estado conhecido da cota da The Odds API, persistido para que o guardrail (#40)
 * continue armado depois de um restart.
 *
 * <p>O snapshot de odds ja sobrevivia ao redeploy, mas o saldo que decide <em>se vale a pena
 * chamar</em> vivia so em memoria: cada deploy voltava para "sem leitura" e desarmava o
 * guardrail justamente no ambiente que o motivou, onde o cache e zerado a cada subida.
 *
 * <p>Linha unica, sempre com {@code id = ID_UNICO}, como o {@link OddsSnapshot}. Fica em
 * tabela separada porque as duas coisas mudam em ritmos diferentes: o snapshot so e reescrito
 * quando vem resposta com jogos, e a cota a cada leitura de header — inclusive nas respostas
 * vazias e nas de erro, que nao podem tocar no snapshot.
 */
@Entity
@Table(name = "odds_cota")
@Getter
@Setter
@NoArgsConstructor
public class OddsCota {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    /** Saldo informado pelo provedor; {@code null} enquanto nenhuma leitura aconteceu. */
    @Column(name = "requests_remaining")
    private Long requestsRemaining;

    /** Consumo do mes informado pelo provedor; {@code null} sem leitura ainda. */
    @Column(name = "requests_used")
    private Long requestsUsed;

    @Column(name = "ultima_leitura")
    private LocalDateTime ultimaLeitura;

    /** Instante da ultima chamada de sondagem liberada pelo guardrail. */
    @Column(name = "ultima_sondagem")
    private LocalDateTime ultimaSondagem;
}
