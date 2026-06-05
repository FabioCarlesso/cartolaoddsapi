package com.cartola.odds.controller;

import com.cartola.odds.controller.api.HistoricoApi;
import com.cartola.odds.model.response.EscalacaoRodadaResponse;
import com.cartola.odds.model.response.HistoricoResponse;
import com.cartola.odds.service.EscalacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HistoricoController implements HistoricoApi {

    private final EscalacaoService escalacaoService;

    @Override
    public ResponseEntity<HistoricoResponse> listar() {
        log.info("GET /api/historico");
        return ResponseEntity.ok(escalacaoService.buscarHistorico());
    }

    @Override
    public ResponseEntity<EscalacaoRodadaResponse> detalhar(Integer rodadaId) {
        log.info("GET /api/historico/{}", rodadaId);
        return ResponseEntity.ok(escalacaoService.buscarPorRodada(rodadaId));
    }

    @Override
    public ResponseEntity<EscalacaoRodadaResponse> atualizarPontuacao(Integer rodadaId) {
        log.info("POST /api/historico/{}/atualizar-pontuacao", rodadaId);
        return ResponseEntity.ok(escalacaoService.atualizarPontuacaoReal(rodadaId));
    }
}
