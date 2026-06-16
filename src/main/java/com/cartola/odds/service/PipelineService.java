package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.FormacaoConfig;
import com.cartola.odds.model.ResultadoFormacao;
import com.cartola.odds.model.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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
        var contexto = prepararContexto();

        log.info("7 - Montando time...");
        Time time = montadorTimeService.montar(
                contexto.pool(),
                contexto.rodada(),
                contexto.avisoMercado(),
                orcamento
        );

        log.info("Pipeline concluido | Rodada {}", contexto.rodada());
        return time;
    }

    /**
     * Executa o pipeline uma unica vez e monta o melhor time para cada formacao
     * informada, reaproveitando o mesmo pool de atletas com scores. A
     * configuracao persistida nao e alterada — a formacao e aplicada apenas em
     * cada montagem.
     *
     * @param formacoes formacoes distintas a comparar
     * @param orcamento orcamento maximo (C$) aplicado a cada formacao; pode ser {@code null}
     * @return um resultado por formacao, na mesma ordem da lista informada
     */
    public List<ResultadoFormacao> compararFormacoes(List<FormacaoConfig> formacoes, Double orcamento) {
        var contexto = prepararContexto();

        log.info("7 - Montando time para {} formacoes...", formacoes.size());
        return formacoes.stream()
                .map(formacao -> new ResultadoFormacao(formacao, montadorTimeService.montar(
                        contexto.pool(),
                        contexto.rodada(),
                        contexto.avisoMercado(),
                        orcamento,
                        formacao
                )))
                .toList();
    }

    /**
     * Executa as etapas 1-6 do pipeline (status do mercado, favoritos, atletas
     * filtrados, desempenho e scores) e devolve o pool pronto para a montagem.
     */
    private ContextoMontagem prepararContexto() {
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

        return new ContextoMontagem(
                atletasComScore,
                statusResponse.getRodadaAtual(),
                statusResponse.getAvisoMercado());
    }

    /** Pool de atletas com scores e dados da rodada, prontos para a montagem do time. */
    private record ContextoMontagem(List<Atleta> pool, int rodada, String avisoMercado) {
    }
}
