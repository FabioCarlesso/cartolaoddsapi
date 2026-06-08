package com.cartola.odds.service;

import com.cartola.odds.model.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final OddsService        oddsService;
    private final CartolaDataService  cartolaDataService;
    private final DesempenhoService   desempenhoService;
    private final ScoreService        scoreService;
    private final MontadorTimeService montadorTimeService;

    /**
     * Pipeline completo de montagem do time:
     *  1. Verifica status do mercado — avisa quando fechado/manutencao/parcial
     *  2. Busca odds e extrai favoritos
     *  3. Busca atletas filtrados
     *  4. Busca times mandantes
     *  5. Calcula media das ultimas 5 rodadas (desempenho real)
     *  6. Calcula score ponderado com desempenho real
     *  7. Monta time (titulares, reservas sem TEC, capitao, substitutos)
     */
    public Time executar() {
        return executar(null);
    }

    /**
     * Executa o pipeline respeitando um orcamento maximo em cartoletas.
     *
     * @param orcamento orcamento maximo (C$); quando {@code null}, nenhuma restricao de custo
     *                  da requisicao e aplicada e vale a estrategia SCORE_MAXIMO.
     */
    public Time executar(Double orcamento) {

        log.info("1 - Verificando status do mercado...");
        var statusResponse = cartolaDataService.buscarStatusMercado();
        var statusMercado  = statusResponse.getStatus();

        if (!statusMercado.isAberto()) {
            log.warn("[MERCADO {}] {} | Rodada: {}",
                    statusMercado.name(),
                    statusMercado.getAviso(),
                    statusResponse.getRodadaAtual());
        } else {
            log.info("Mercado aberto | Rodada: {}", statusResponse.getRodadaAtual());
        }

        log.info("2 - Buscando dados da rodada e identificando favoritos...");
        var dadosRodada = cartolaDataService.buscarDadosRodada();
        Set<String> favoritos = oddsService.buscarFavoritos(dadosRodada.confrontos());

        log.info("3 - Buscando atletas do Cartola...");
        var atletasFiltrados = cartolaDataService.buscarAtletasFiltrados(favoritos);

        if (atletasFiltrados.isEmpty()) {
            throw new IllegalStateException(
                "Nenhum atleta disponivel. Verifique a Odds API Key e o valor de ODD_LIMITE.");
        }

        log.info("4 - Identificando times mandantes...");
        Set<Integer> timesCasa = dadosRodada.timesCasa();

        log.info("5 - Calculando desempenho das ultimas {} rodadas...", DesempenhoService.RODADAS_HISTORICO);
        var desempenhoMap = desempenhoService.calcularDesempenhoUltimasRodadas(statusResponse.getRodadaAtual());

        log.info("6 - Calculando scores com desempenho real...");
        var atletasComScore = scoreService.calcularScores(atletasFiltrados, timesCasa, favoritos, desempenhoMap);

        log.info("7 - Montando time...");
        Time time = montadorTimeService.montar(
                atletasComScore,
                statusResponse.getRodadaAtual(),
                statusResponse.getAvisoMercado(),
                orcamento
        );

        log.info("Pipeline concluido | Rodada {} | Status mercado: {}",
                statusResponse.getRodadaAtual(), statusMercado.getLabel());
        return time;
    }
}
