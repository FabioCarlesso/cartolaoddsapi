package com.cartola.odds.service;

import com.cartola.odds.config.AppProperties;
import com.cartola.odds.model.Atleta;
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

    private final AppProperties props;

    /**
     * Calcula o score ponderado de cada atleta.
     *
     * Formula:
     *   score = (mediaPontos      x peso_media_pontos)
     *         + (valorização      x peso_valorizacao)
     *         + (desempenho       x peso_desempenho)   ← media ultimas 5 rodadas (real)
     *         + (fatorCasa        x peso_fator_casa)   ← 10.0 se mandante, 0 caso contrario
     *         + (timeFavorito     x peso_time_favorito)← 10.0 se favorito pelas odds
     *
     * Desempenho:
     *   - Se o mapa de desempenho contiver o atletaId -> usa a media real das ultimas 5 rodadas
     *   - Caso contrario -> usa mediaPontos como proxy (comportamento anterior)
     *
     * @param atletas          lista de atletas a pontuar
     * @param timesCasa        IDs dos clubes mandantes na rodada
     * @param favoritos        nomes normalizados dos times favoritos
     * @param desempenhoMap    mapa {atletaId -> media ultimas 5 rodadas} (pode ser vazio)
     */
    public List<Atleta> calcularScores(List<Atleta> atletas,
                                       Set<Integer> timesCasa,
                                       Set<String>  favoritos,
                                       Map<Integer, Double> desempenhoMap) {
        var pesos = props.getScore().getPeso();

        int comDesempenhoReal = 0;
        int comProxy          = 0;

        var resultado = atletas.stream()
                .map(a -> {
                    double fatorCasa    = timesCasa.contains(a.getClubeId()) ? 10.0 : 0.0;
                    double timeFavorito = favoritos.contains(a.getNomeClubeNorm()) ? 10.0 : 0.0;

                    // Desempenho: media real das ultimas 5 rodadas ou proxy (mediaPontos)
                    double desempenho = desempenhoMap.getOrDefault(a.getAtletaId(), 0.0);
                    boolean usouReal  = desempenho > 0.0;
                    if (!usouReal) desempenho = a.getMediaPontos();  // fallback

                    double score = (a.getMediaPontos() * pesos.getMediaPontos())
                                 + (a.getValorizacao()  * pesos.getValorizacao())
                                 + (desempenho          * pesos.getDesempenho())
                                 + (fatorCasa           * pesos.getFatorCasa())
                                 + (timeFavorito        * pesos.getTimeFavorito());

                    return a.withDesempenhoRecente(desempenho)
                            .withScore(Math.round(score * 10000.0) / 10000.0);
                })
                .toList();

        // Log resumo de uso do historico real
        long real  = resultado.stream().filter(a -> a.getDesempenhoRecente() > 0 && a.getDesempenhoRecente() != a.getMediaPontos()).count();
        long proxy = resultado.size() - real;
        log.info("Score calculado | com historico real: {} | com proxy: {}", real, proxy);

        return resultado;
    }

    /**
     * Sobrecarga sem mapa de desempenho — usa mediaPontos como proxy para todos.
     * Mantido para compatibilidade com testes unitarios e cenarios sem historico.
     */
    public List<Atleta> calcularScores(List<Atleta> atletas,
                                       Set<Integer> timesCasa,
                                       Set<String>  favoritos) {
        return calcularScores(atletas, timesCasa, favoritos, Map.of());
    }
}
