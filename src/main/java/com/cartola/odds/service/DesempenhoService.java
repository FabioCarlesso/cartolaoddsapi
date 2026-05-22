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
 * Calcula o desempenho dos atletas nas ultimas N rodadas.
 *
 * A API do Cartola FC expoe /atletas/pontuados com os dados da rodada atual.
 * Para obter historico, buscamos as rodadas anteriores (rodada_atual - 1, -2, ...).
 *
 * Resultado retornado: Map<atletaId, DesempenhoAtleta> com media, desvio padrao
 * e numero de rodadas consideradas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesempenhoService {

    static final int RODADAS_HISTORICO = 5;

    private final CartolaClient cartolaClient;

    /**
     * Desempenho recente de um atleta nas ultimas rodadas.
     *
     * @param mediaPontos         media de pontuacao das rodadas consideradas
     * @param desvioPadrao        desvio padrao populacional das pontuacoes (0.0 com menos de 2 rodadas)
     * @param rodadasConsideradas quantidade de rodadas com pontuacao disponivel para o atleta
     */
    public record DesempenhoAtleta(
            double mediaPontos,
            double desvioPadrao,
            int rodadasConsideradas
    ) {}

    /**
     * Calcula o desempenho das ultimas {@code RODADAS_HISTORICO} rodadas
     * para cada atleta disponivel no historico.
     *
     * @param rodadaAtual rodada atual retornada pelo /mercado/status
     * @return mapa {atletaId -> DesempenhoAtleta das ultimas 5 rodadas}
     *         Atletas sem historico nao aparecem no mapa (fallback para mediaPontos da temporada).
     */
    public Map<Integer, DesempenhoAtleta> calcularDesempenhoUltimasRodadas(int rodadaAtual) {
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

        // Calcula media e desvio padrao por atleta
        var desempenhos = pontuacoesPorAtleta.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            List<Double> pontos = e.getValue();
                            double media = pontos.stream()
                                    .mapToDouble(Double::doubleValue)
                                    .average()
                                    .orElse(0.0);
                            return new DesempenhoAtleta(media, calcularDesvio(pontos, media), pontos.size());
                        }
                ));

        log.info("Desempenho calculado para {} atletas (baseado em {} rodadas)",
                desempenhos.size(), rodadasAlvo.size());
        return desempenhos;
    }

    /**
     * Desvio padrao populacional (divisao por N) das pontuacoes.
     * Retorna 0.0 quando ha menos de 2 rodadas, sem quebrar por dados insuficientes.
     */
    private double calcularDesvio(List<Double> pontos, double media) {
        if (pontos.size() < 2) return 0.0;
        double variancia = pontos.stream()
                .mapToDouble(p -> (p - media) * (p - media))
                .average()
                .orElse(0.0);
        return Math.sqrt(variancia);
    }
}
