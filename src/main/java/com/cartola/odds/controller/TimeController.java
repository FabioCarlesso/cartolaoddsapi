package com.cartola.odds.controller;

import com.cartola.odds.controller.api.TimeApi;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.response.TimeResponse;
import com.cartola.odds.service.EscalacaoService;
import com.cartola.odds.service.PipelineService;
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
    public ResponseEntity<TimeResponse> montarTime() {
        log.info("GET /api/time - Iniciando pipeline...");
        var time = pipelineService.executar();
        registrarEscalacao(time);
        return ResponseEntity.ok(TimeResponse.from(time));
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
