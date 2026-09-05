package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.response.OddsCotaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OddsCotaService {

    private final OddsClient     oddsClient;
    private final OddsProperties oddsProperties;

    public OddsCotaResponse buscarCota() {
        return OddsCotaResponse.builder()
                .saldoRestante(oddsClient.getRequestsRemaining())
                .consumoMes(oddsClient.getRequestsUsed())
                .ultimaLeitura(oddsClient.getUltimaLeitura())
                .minRequestsRemaining(oddsProperties.getMinRequestsRemaining())
                .guardrailAtivo(oddsClient.isGuardrailAtivo())
                .ultimaSondagem(oddsClient.getUltimaSondagem())
                .proximaSondagem(oddsClient.getProximaSondagem())
                .build();
    }
}
