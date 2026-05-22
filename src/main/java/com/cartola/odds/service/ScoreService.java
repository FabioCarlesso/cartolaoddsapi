package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Configuracao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ConfiguracaoService configuracaoService;

    // --- Pesos específicos por posição: Goleiro ---
    // Prioriza desempenho recente e scouts defensivos (DD, DP) com penalização por gols sofridos
    static final double GOL_PESO_DESEMPENHO       = 0.35;
    static final double GOL_PESO_MEDIA_PONTOS      = 0.25;
    static final double GOL_PESO_VALORIZACAO       = 0.10;
    static final double GOL_PESO_DEFESAS_DIFICEIS  = 0.05;
    static final double GOL_PESO_PENALTIS_DEF      = 0.05;
    static final double GOL_PENALIZACAO_GOLS_SFD   = 0.02;

    // --- Pesos específicos por posição: Atacante ---
    // Prioriza participação ofensiva: gols e assistências além do desempenho recente
    static final double ATA_PESO_DESEMPENHO   = 0.25;
    static final double ATA_PESO_MEDIA_PONTOS  = 0.25;
    static final double ATA_PESO_VALORIZACAO   = 0.10;
    static final double ATA_PESO_GOLS          = 0.08;
    static final double ATA_PESO_ASSISTENCIAS  = 0.05;

    public List<Atleta> calcularScores(List<Atleta> atletas,
                                       Set<Integer> timesCasa,
                                       Set<String>  favoritos,
                                       Map<Integer, DesempenhoService.DesempenhoAtleta> desempenhoMap) {
        Configuracao config = configuracaoService.buscarConfig();

        var resultado = atletas.stream()
                .map(a -> {
                    double fatorCasa    = timesCasa.contains(a.getClubeId()) ? 10.0 : 0.0;
                    double timeFavorito = favoritos.contains(a.getNomeClubeNorm()) ? 10.0 : 0.0;

                    DesempenhoService.DesempenhoAtleta desempenhoAtleta = desempenhoMap.get(a.getAtletaId());
                    boolean temHistorico = desempenhoAtleta != null && desempenhoAtleta.rodadasConsideradas() > 0;

                    // Com histórico real, usa a média das últimas rodadas; sem ele, cai para a média da temporada (proxy)
                    double desempenho   = temHistorico ? desempenhoAtleta.mediaPontos() : a.getMediaPontos();
                    // O desvio só existe para o histórico real; o proxy não tem volatilidade associada
                    double desvioPadrao = temHistorico ? desempenhoAtleta.desvioPadrao() : 0.0;
                    int rodadasConsideradas = temHistorico ? desempenhoAtleta.rodadasConsideradas() : 0;

                    double score = switch (a.getPosicao()) {
                        case GOL -> calcularGoleiro(a, desempenho, fatorCasa, timeFavorito, config);
                        case ATA -> calcularAtacante(a, desempenho, fatorCasa, timeFavorito, config);
                        default  -> calcularDefault(a, desempenho, fatorCasa, timeFavorito, config);
                    };

                    // Penaliza volatilidade: atletas inconsistentes perdem pontos em empate tecnico
                    score -= desvioPadrao * config.getPesoDesvio();

                    return a.withDesempenhoRecente(desempenho)
                            .withDesvioPadrao(Math.round(desvioPadrao * 10000.0) / 10000.0)
                            .withRodadasConsideradas(rodadasConsideradas)
                            .withScore(Math.round(score * 10000.0) / 10000.0);
                })
                .toList();

        long real  = resultado.stream().filter(a -> a.getDesempenhoRecente() > 0 && a.getDesempenhoRecente() != a.getMediaPontos()).count();
        long proxy = resultado.size() - real;
        log.info("Score calculado | com historico real: {} | com proxy: {}", real, proxy);

        return resultado;
    }

    public List<Atleta> calcularScores(List<Atleta> atletas,
                                       Set<Integer> timesCasa,
                                       Set<String>  favoritos) {
        return calcularScores(atletas, timesCasa, favoritos, Map.of());
    }

    // Goleiro: maior peso em desempenho e scouts defensivos
    private double calcularGoleiro(Atleta a, double desempenho,
                                   double fatorCasa, double timeFavorito,
                                   Configuracao config) {
        return (desempenho              * GOL_PESO_DESEMPENHO)
             + (a.getMediaPontos()      * GOL_PESO_MEDIA_PONTOS)
             + (a.getValorizacao()      * GOL_PESO_VALORIZACAO)
             + (a.getDefesasDificeis()  * GOL_PESO_DEFESAS_DIFICEIS)
             + (a.getPenaltisDefendidos() * GOL_PESO_PENALTIS_DEF)
             - (a.getGolsSofridos()     * GOL_PENALIZACAO_GOLS_SFD)
             + (fatorCasa               * config.getPesoFatorCasa())
             + (timeFavorito            * config.getPesoTimeFavorito());
    }

    // Atacante: maior peso em participação ofensiva (gols e assistências)
    private double calcularAtacante(Atleta a, double desempenho,
                                    double fatorCasa, double timeFavorito,
                                    Configuracao config) {
        return (desempenho          * ATA_PESO_DESEMPENHO)
             + (a.getMediaPontos()  * ATA_PESO_MEDIA_PONTOS)
             + (a.getValorizacao()  * ATA_PESO_VALORIZACAO)
             + (a.getGols()         * ATA_PESO_GOLS)
             + (a.getAssistencias() * ATA_PESO_ASSISTENCIAS)
             + (fatorCasa           * config.getPesoFatorCasa())
             + (timeFavorito        * config.getPesoTimeFavorito());
    }

    // Fallback para posições sem regra específica (LAT, ZAG, MEI, TEC)
    private double calcularDefault(Atleta a, double desempenho,
                                   double fatorCasa, double timeFavorito,
                                   Configuracao config) {
        return (a.getMediaPontos() * config.getPesoMediaPontos())
             + (a.getValorizacao()  * config.getPesoValorizacao())
             + (desempenho          * config.getPesoDesempenho())
             + (fatorCasa           * config.getPesoFatorCasa())
             + (timeFavorito        * config.getPesoTimeFavorito());
    }
}
