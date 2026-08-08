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
        if (excluirDuvida) {
            log.info("Consulta comparativa (excluirDuvida=true) - escalacao da rodada {} nao registrada",
                    time.getRodada());
        } else {
            registrarEscalacao(time);
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
     * Persiste a escalacao sugerida de forma nao bloqueante: falhas ao salvar
     * sao logadas mas nao impedem o retorno do time.
     *
     * <p>Chamado em toda montagem exceto {@code excluirDuvida=true}. Como
     * {@link EscalacaoService#salvarEscalacao} e idempotente por rodada (a primeira chamada
     * vence), registrar a variante sem duvidas faria o historico gravar a primeira consulta
     * feita na rodada em vez da sugestao canonica — cenario provavel, já que comparar
     * {@code /api/time} com {@code /api/time?excluirDuvida=true} e o uso natural do parametro.
     *
     * <p>O {@code orcamento}, ao contrario, restringe o time a um teto real de cartoletas e
     * continua produzindo a escalacao que o usuario de fato vai usar — por isso persiste
     * normalmente.
     */
    private void registrarEscalacao(Time time) {
        try {
            escalacaoService.salvarEscalacao(time, time.getRodada());
        } catch (Exception ex) {
            log.error("Falha ao registrar escalacao da rodada {}: {}", time.getRodada(), ex.getMessage(), ex);
        }
    }
}
