package com.cartola.odds.controller;

import com.cartola.odds.controller.api.TimeApi;
import com.cartola.odds.model.response.TimeResponse;
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

    @Override
    public ResponseEntity<TimeResponse> montarTime() {
        log.info("GET /api/time - Iniciando pipeline...");
        var time = pipelineService.executar();
        return ResponseEntity.ok(TimeResponse.from(time));
    }
}
