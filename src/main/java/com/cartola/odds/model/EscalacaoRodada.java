package com.cartola.odds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de um atleta escalado em uma rodada especifica.
 *
 * Persiste o time sugerido para permitir analise retroativa: comparar o score
 * sugerido com a pontuacao real obtida apos o fechamento da rodada.
 *
 * A {@code pontuacaoReal} fica null ate que a rodada seja atualizada via
 * {@code POST /api/historico/{rodadaId}/atualizar-pontuacao}.
 */
@Entity
@Table(name = "escalacao_rodada")
@Getter
@Setter
@NoArgsConstructor
public class EscalacaoRodada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rodada_id", nullable = false)
    private Integer rodadaId;

    @Column(name = "atleta_id", nullable = false)
    private Integer atletaId;

    @Column(name = "apelido", nullable = false, length = 100)
    private String apelido;

    @Column(name = "posicao", nullable = false, length = 10)
    private String posicao;

    @Column(name = "clube", nullable = false, length = 100)
    private String clube;

    @Column(name = "score_sugerido", nullable = false)
    private double scoreSugerido;

    @Column(name = "preco", nullable = false)
    private double preco;

    @Column(name = "capitao", nullable = false)
    private boolean capitao;

    @Column(name = "reserva_luxo", nullable = false)
    private boolean reservaLuxo;

    @Column(name = "em_duvida", nullable = false)
    private boolean emDuvida;

    /** Pontuacao real obtida na rodada — null ate a rodada ser atualizada. */
    @Column(name = "pontuacao_real")
    private Double pontuacaoReal;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
