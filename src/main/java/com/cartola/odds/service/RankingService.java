package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.response.RankingResponse;
import com.cartola.odds.model.response.RankingResponse.AtletaRankingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    static final int LIMITE_PADRAO = 25;
    static final int LIMITE_MAXIMO = 100;

    private final OddsService        oddsService;
    private final CartolaDataService  cartolaDataService;
    private final DesempenhoService   desempenhoService;
    private final ScoreService        scoreService;

    public RankingResponse buscarRanking(Posicao posicao, int limite) {
        int limiteValido = Math.max(1, Math.min(limite, LIMITE_MAXIMO));

        log.info("Buscando ranking | posicao={} | limite={}",
                posicao != null ? posicao : "TODAS", limiteValido);

        Set<String>  favoritos       = oddsService.buscarFavoritos();
        Set<Integer> timesCasa       = cartolaDataService.buscarTimesCasa();
        var          statusResponse  = cartolaDataService.buscarStatusMercado();
        var          statusMercado   = statusResponse.getStatus();

        if (!statusMercado.isAberto()) {
            log.warn("[MERCADO {}] {} | Rodada: {}",
                    statusMercado.name(), statusMercado.getAviso(), statusResponse.getRodadaAtual());
        }

        var atletasFiltrados = cartolaDataService.buscarAtletasFiltrados(favoritos);
        var desempenhoMap    = desempenhoService.calcularMediaUltimasRodadas(statusResponse.getRodadaAtual());
        var atletasComScore  = scoreService.calcularScores(atletasFiltrados, timesCasa, favoritos, desempenhoMap);

        Stream<Atleta> stream = atletasComScore.stream()
                .sorted(Comparator.comparingDouble(Atleta::getScore).reversed());

        if (posicao != null) {
            stream = stream.filter(a -> a.getPosicao() == posicao);
        }

        List<Atleta> pool = stream.toList();

        var contador = new AtomicInteger(1);
        List<AtletaRankingDto> ranking = pool.stream()
                .limit(limiteValido)
                .map(a -> AtletaRankingDto.from(a, contador.getAndIncrement()))
                .toList();

        log.info("Ranking gerado: {} de {} disponiveis | Mercado: {}",
                ranking.size(), pool.size(), statusMercado.getLabel());

        return RankingResponse.builder()
                .rodada(statusResponse.getRodadaAtual())
                .avisoMercado(statusResponse.getAvisoMercado())
                .posicao(posicao != null ? posicao.name() : "TODAS")
                .limite(limiteValido)
                .totalDisponivel(pool.size())
                .atletas(ranking)
                .build();
    }
}
