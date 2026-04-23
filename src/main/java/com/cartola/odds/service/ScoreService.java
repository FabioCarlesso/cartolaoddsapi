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

    public List<Atleta> calcularScores(List<Atleta> atletas,
                                       Set<Integer> timesCasa,
                                       Set<String>  favoritos,
                                       Map<Integer, Double> desempenhoMap) {
        Configuracao config = configuracaoService.buscarConfig();

        var resultado = atletas.stream()
                .map(a -> {
                    double fatorCasa    = timesCasa.contains(a.getClubeId()) ? 10.0 : 0.0;
                    double timeFavorito = favoritos.contains(a.getNomeClubeNorm()) ? 10.0 : 0.0;

                    double desempenho = desempenhoMap.getOrDefault(a.getAtletaId(), 0.0);
                    if (desempenho == 0.0) desempenho = a.getMediaPontos();

                    double score = (a.getMediaPontos() * config.getPesoMediaPontos())
                                 + (a.getValorizacao()  * config.getPesoValorizacao())
                                 + (desempenho          * config.getPesoDesempenho())
                                 + (fatorCasa           * config.getPesoFatorCasa())
                                 + (timeFavorito        * config.getPesoTimeFavorito());

                    return a.withDesempenhoRecente(desempenho)
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
}
