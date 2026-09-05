package com.cartola.odds.controller;

import com.cartola.odds.controller.api.OddsCotaApi;
import com.cartola.odds.model.response.OddsCotaResponse;
import com.cartola.odds.service.OddsCotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OddsCotaController implements OddsCotaApi {

    private final OddsCotaService oddsCotaService;

    @Override
    public ResponseEntity<OddsCotaResponse> buscarCota() {
        log.info("GET /api/odds/cota");
        return ResponseEntity.ok(oddsCotaService.buscarCota());
    }
}
