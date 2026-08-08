package com.cartola.odds.controller;

import com.cartola.odds.controller.api.TimeApi;
import com.cartola.odds.model.FormacaoConfig;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.response.CompararFormacoesResponse;
import com.cartola.odds.model.response.TimeResponse;
import com.cartola.odds.service.EscalacaoService;
import com.cartola.odds.service.PipelineService;
import com.cartola.odds.util.FormacaoParser;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TimeController implements TimeApi {

    private final PipelineService pipelineService;
    private final EscalacaoService escalacaoService;

    @Override
    public ResponseEntity<TimeResponse> montarTime(Double orcamento, boolean excluirDuvida) {
        validarOrcamento(orcamento);
        log.info("GET /api/time - Iniciando pipeline... | orcamento={} | excluirDuvida={}",
                orcamento, excluirDuvida);
        var time = pipelineService.executar(orcamento, excluirDuvida);
        if (isConsultaPadrao(orcamento, excluirDuvida)) {
            registrarEscalacao(time);
        } else {
            log.info("Consulta exploratoria (orcamento={} | excluirDuvida={}) - escalacao da rodada {} nao registrada",
                    orcamento, excluirDuvida, time.getRodada());
        }
        return ResponseEntity.ok(TimeResponse.from(time));
    }

    @Override
    public ResponseEntity<CompararFormacoesResponse> compararFormacoes(String formacoes, Double orcamento) {
        validarOrcamento(orcamento);
        List<FormacaoConfig> formacoesValidadas = FormacaoParser.parseLista(formacoes);
        log.info("GET /api/time/comparar - Comparando {} formacoes | orcamento={}",
                formacoesValidadas.size(), orcamento);
        var resultados = pipelineService.compararFormacoes(formacoesValidadas, orcamento);
        return ResponseEntity.ok(CompararFormacoesResponse.from(resultados));
    }

    private void validarOrcamento(Double orcamento) {
        if (orcamento != null && orcamento <= 0) {
            throw new IllegalArgumentException(
                    "orcamento deve ser maior que 0. Valor informado: " + orcamento);
        }
    }

    /**
     * Somente a montagem padrao — sem {@code orcamento} e sem {@code excluirDuvida} — representa
     * "a sugestao da rodada" e deve alimentar o historico.
     *
     * <p>Como {@link EscalacaoService#salvarEscalacao} e idempotente por rodada (a primeira
     * chamada vence), persistir consultas parametrizadas faria o historico registrar a primeira
     * variante consultada em vez da sugestao canonica — por exemplo ao comparar
     * {@code /api/time} com {@code /api/time?excluirDuvida=true} na mesma rodada. Consultas
     * exploratorias sao tratadas como o {@code /api/time/comparar}, que ja nao persiste.
     */
    private boolean isConsultaPadrao(Double orcamento, boolean excluirDuvida) {
        return orcamento == null && !excluirDuvida;
    }

    /**
     * Persiste a escalacao sugerida de forma nao bloqueante: falhas ao salvar
     * sao logadas mas nao impedem o retorno do time.
     */
    private void registrarEscalacao(Time time) {
        try {
            escalacaoService.salvarEscalacao(time, time.getRodada());
        } catch (Exception ex) {
            log.error("Falha ao registrar escalacao da rodada {}: {}", time.getRodada(), ex.getMessage(), ex);
        }
    }
}
