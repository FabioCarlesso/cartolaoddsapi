package com.cartola.odds.controller;

import com.cartola.odds.controller.api.RankingApi;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.response.RankingResponse;
import com.cartola.odds.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RankingController implements RankingApi {

    private final RankingService rankingService;

    @Override
    public ResponseEntity<RankingResponse> ranking(String posicao, int limite) {
        log.info("GET /api/ranking | posicao={} | limite={}", posicao, limite);
        Posicao posicaoEnum = resolverPosicao(posicao);
        return ResponseEntity.ok(rankingService.buscarRanking(posicaoEnum, limite));
    }

    private Posicao resolverPosicao(String posicao) {
        if (posicao == null || posicao.isBlank()) return null;
        return Posicao.fromSigla(posicao.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Posicao invalida: '%s'. Valores aceitos: GOL, LAT, ZAG, MEI, ATA, TEC"
                                .formatted(posicao)));
    }
}
