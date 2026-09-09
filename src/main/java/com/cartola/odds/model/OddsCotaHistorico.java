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
 * Uma leitura de cota da The Odds API, guardada para formar a serie do mes (#61).
 *
 * <p>Complementa {@link OddsCota} em vez de substitui-la: a linha unica continua sendo o estado
 * corrente — o que o guardrail (#40) recupera no boot para nascer armado —, e aqui fica o
 * historico, que e o que responde "quanto eu gastei ao longo deste mes". Sobrescrever uma linha
 * so nunca poderia responder isso, e foi por nao existir resposta dentro da aplicacao que a
 * pergunta quase virou um Prometheus ao lado (#58, PR #60).
 *
 * <p>Append-only: nada aqui e atualizado depois de gravado. O volume se limita sozinho, porque
 * uma linha so nasce de uma chamada ao provedor e as chamadas sao limitadas pela cota que esta
 * sendo medida.
 */
@Entity
@Table(name = "odds_cota_historico")
@Getter
@Setter
@NoArgsConstructor
public class OddsCotaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Instante da leitura — o mesmo gravado em {@link OddsCota#getUltimaLeitura()}. */
    @Column(name = "instante", nullable = false)
    private LocalDateTime instante;

    /** Saldo lido em {@code x-requests-remaining}; {@code null} quando o header nao veio. */
    @Column(name = "saldo_restante")
    private Long saldoRestante;

    /** Consumo do mes lido em {@code x-requests-used}; {@code null} quando o header nao veio. */
    @Column(name = "consumo_mes")
    private Long consumoMes;
}
