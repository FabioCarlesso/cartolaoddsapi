package com.cartola.odds.controller;

import com.cartola.odds.controller.api.ConfiguracaoApi;
import com.cartola.odds.model.request.ConfiguracaoRequest;
import com.cartola.odds.model.response.ConfiguracaoResponse;
import com.cartola.odds.service.ConfiguracaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ConfiguracaoController implements ConfiguracaoApi {

    private final ConfiguracaoService configuracaoService;

    @Override
    public ResponseEntity<ConfiguracaoResponse> buscar() {
        log.info("GET /api/config");
        return ResponseEntity.ok(ConfiguracaoResponse.from(configuracaoService.buscarConfig()));
    }

    @Override
    public ResponseEntity<ConfiguracaoResponse> atualizar(ConfiguracaoRequest request) {
        log.info("PATCH /api/config");
        return ResponseEntity.ok(configuracaoService.atualizar(request));
    }

    @Override
    public ResponseEntity<ConfiguracaoResponse> resetar() {
        log.info("POST /api/config/reset");
        return ResponseEntity.ok(configuracaoService.resetar());
    }
}
