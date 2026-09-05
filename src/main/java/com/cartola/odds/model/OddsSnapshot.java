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
 * Ultima resposta de odds bem-sucedida da The Odds API, persistida para que o guardrail de
 * cota (#40) sirva fallback que sobrevive a restart e redeploy, em vez de depender so do
 * cache Caffeine em memoria (zerado a cada boot).
 *
 * Linha unica, sempre com {@code id = ID_UNICO}: cada resposta nova sobrescreve a anterior,
 * ja que o interesse e apenas na ultima leitura conhecida.
 */
@Entity
@Table(name = "odds_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class OddsSnapshot {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(name = "odds_json", nullable = false, columnDefinition = "TEXT")
    private String oddsJson;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
