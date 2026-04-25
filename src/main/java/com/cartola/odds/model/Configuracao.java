package com.cartola.odds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "configuracao")
@Getter
@Setter
@NoArgsConstructor
public class Configuracao {

    @Id
    private Long id;

    @Column(name = "odd_limite", nullable = false)
    private double oddLimite;

    @Column(name = "peso_media_pontos", nullable = false)
    private double pesoMediaPontos;

    @Column(name = "peso_valorizacao", nullable = false)
    private double pesoValorizacao;

    @Column(name = "peso_desempenho", nullable = false)
    private double pesoDesempenho;

    @Column(name = "peso_fator_casa", nullable = false)
    private double pesoFatorCasa;

    @Column(name = "peso_time_favorito", nullable = false)
    private double pesoTimeFavorito;

    @Column(name = "formacao_gol", nullable = false)
    private int formacaoGol;

    @Column(name = "formacao_lat", nullable = false)
    private int formacaoLat;

    @Column(name = "formacao_zag", nullable = false)
    private int formacaoZag;

    @Column(name = "formacao_mei", nullable = false)
    private int formacaoMei;

    @Column(name = "formacao_ata", nullable = false)
    private int formacaoAta;

    @Column(name = "formacao_tec", nullable = false)
    private int formacaoTec;

    @Column(name = "evitar_mesmo_clube_defesa", nullable = false)
    private boolean evitarMesmoClubeDefesa;

    @Column(name = "limite_atletas_por_clube", nullable = false)
    private int limiteAtletasPorClube;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Map<String, Integer> getFormacaoAsMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("GOL", formacaoGol);
        map.put("LAT", formacaoLat);
        map.put("ZAG", formacaoZag);
        map.put("MEI", formacaoMei);
        map.put("ATA", formacaoAta);
        map.put("TEC", formacaoTec);
        return map;
    }

    public static Configuracao defaults() {
        var c = new Configuracao();
        c.setId(1L);
        c.setOddLimite(3.0);
        c.setPesoMediaPontos(0.40);
        c.setPesoValorizacao(0.20);
        c.setPesoDesempenho(0.20);
        c.setPesoFatorCasa(0.10);
        c.setPesoTimeFavorito(0.10);
        c.setFormacaoGol(1);
        c.setFormacaoLat(2);
        c.setFormacaoZag(2);
        c.setFormacaoMei(3);
        c.setFormacaoAta(3);
        c.setFormacaoTec(1);
        c.setEvitarMesmoClubeDefesa(true);
        c.setLimiteAtletasPorClube(4);
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }
}
