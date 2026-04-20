package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.model.response.PontuadosResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Calcula a media de pontuacao dos atletas nas ultimas N rodadas.
 *
 * A API do Cartola FC expoe /atletas/pontuados com os dados da rodada atual.
 * Para obter historico, buscamos as rodadas anteriores (rodada_atual - 1, -2, ...).
 *
 * Resultado retornado: Map<atletaId, mediaUltimasRodadas>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesempenhoService {

    static final int RODADAS_HISTORICO = 5;

    private final CartolaClient cartolaClient;

    /**
     * Calcula a media de pontuacao das ultimas {@code RODADAS_HISTORICO} rodadas
     * para cada atleta disponivel no historico.
     *
     * @param rodadaAtual rodada atual retornada pelo /mercado/status
     * @return mapa {atletaId -> media de pontuacao das ultimas 5 rodadas}
     *         Atletas sem historico nao aparecem no mapa (fallback para mediaPontos da temporada).
     */
    public Map<Integer, Double> calcularMediaUltimasRodadas(int rodadaAtual) {
        // Determina quais rodadas buscar (ate 5 anteriores a atual)
        int primeiraRodada = Math.max(1, rodadaAtual - RODADAS_HISTORICO);
        List<Integer> rodadasAlvo = IntStream.range(primeiraRodada, rodadaAtual)
                .boxed()
                .collect(Collectors.toList());

        if (rodadasAlvo.isEmpty()) {
            log.warn("Nenhuma rodada anterior disponivel para calcular desempenho (rodadaAtual={})", rodadaAtual);
            return Map.of();
        }

        log.info("Calculando desempenho das rodadas {} a {} (ultimas {} rodadas)",
                primeiraRodada, rodadaAtual - 1, rodadasAlvo.size());

        // Acumula pontuacoes por atleta_id
        // Map<atletaId, List<pontuacoes>>
        Map<Integer, List<Double>> pontuacoesPorAtleta = new java.util.HashMap<>();

        for (int rodada : rodadasAlvo) {
            PontuadosResponse pontuados = cartolaClient.buscarPontuados(rodada);
            if (pontuados == null || pontuados.getAtletas() == null) {
                log.debug("Sem dados de pontuados para rodada {}", rodada);
                continue;
            }

            pontuados.getAtletas().forEach((idStr, pontuado) -> {
                if (pontuado.getPontuacaoNum() == null) return;
                try {
                    int atletaId = Integer.parseInt(idStr);
                    pontuacoesPorAtleta
                            .computeIfAbsent(atletaId, k -> new ArrayList<>())
                            .add(pontuado.getPontuacaoNum());
                } catch (NumberFormatException e) {
                    log.debug("ID de atleta invalido: {}", idStr);
                }
            });
        }

        // Calcula media por atleta
        var medias = pontuacoesPorAtleta.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0)
                ));

        log.info("Medias calculadas para {} atletas (baseado em {} rodadas)",
                medias.size(), rodadasAlvo.size());
        return medias;
    }
}
