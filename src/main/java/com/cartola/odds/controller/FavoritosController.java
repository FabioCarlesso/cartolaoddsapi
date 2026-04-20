package com.cartola.odds.controller;

import com.cartola.odds.config.AppProperties;
import com.cartola.odds.controller.api.FavoritosApi;
import com.cartola.odds.model.response.FavoritosResponse;
import com.cartola.odds.service.OddsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FavoritosController implements FavoritosApi {

    private final OddsService   oddsService;
    private final AppProperties props;

    @Override
    public ResponseEntity<FavoritosResponse> listarFavoritos(Double oddLimite) {
        double limite = resolverLimite(oddLimite);
        log.info("GET /api/favoritos | oddLimite={}", limite);
        return ResponseEntity.ok(oddsService.buscarFavoritosDetalhado(limite));
    }

    private double resolverLimite(Double oddLimite) {
        if (oddLimite == null) return props.getOddLimite();
        if (oddLimite <= 1.0) throw new IllegalArgumentException(
                "oddLimite deve ser maior que 1.0. Valor informado: " + oddLimite);
        return oddLimite;
    }
}
