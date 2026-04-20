package com.cartola.odds.client;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.response.OddsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class OddsClient {

    private final RestClient    restClient;
    private final OddsProperties props;

    public OddsClient(@Qualifier("oddsRestClient") RestClient restClient,
                      OddsProperties props) {
        this.restClient = restClient;
        this.props      = props;
    }

    /**
     * Busca as odds do Brasileirao.
     * Resultado cacheado por 10 minutos — odds nao mudam com frequencia durante o dia.
     * Retorna lista vazia em caso de falha para nao interromper o pipeline.
     */
    @Cacheable(CacheConfig.CACHE_ODDS)
    public List<OddsResponse> buscarOdds() {
        if ("SUA_API_KEY_AQUI".equals(props.getKey())) {
            log.warn("Odds API Key nao configurada. Rodando sem filtro de favoritos.");
            return Collections.emptyList();
        }

        try {
            var uri = "/sports/%s/odds".formatted(props.getSport());
            log.debug("Buscando odds: {}", uri);

            List<OddsResponse> odds = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(uri)
                            .queryParam("regions", props.getRegions())
                            .queryParam("markets", props.getMarkets())
                            .queryParam("apiKey", props.getKey())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            log.info("Odds recebidas: {} jogos (cacheado por 10 min)", odds != null ? odds.size() : 0);
            return odds != null ? odds : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Erro ao buscar odds: {}. Rodando sem filtro de favoritos.", e.getMessage());
            return Collections.emptyList();
        }
    }
}
