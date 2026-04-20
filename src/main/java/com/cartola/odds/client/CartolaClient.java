package com.cartola.odds.client;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.model.response.AtletaResponse;
import com.cartola.odds.model.response.ClubeResponse;
import com.cartola.odds.model.response.MercadoStatusResponse;
import com.cartola.odds.model.response.PartidaResponse;
import com.cartola.odds.model.response.PontuadosResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
public class CartolaClient {

    private final RestClient restClient;

    public CartolaClient(@Qualifier("cartolaRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Cacheable(CacheConfig.CACHE_STATUS_MERCADO)
    public MercadoStatusResponse buscarStatusMercado() {
        log.debug("Buscando status do mercado Cartola...");
        return restClient.get()
                .uri("/mercado/status")
                .retrieve()
                .body(MercadoStatusResponse.class);
    }

    @Cacheable(CacheConfig.CACHE_ATLETAS)
    public AtletaResponse buscarAtletas() {
        log.debug("Buscando atletas do mercado Cartola...");
        return restClient.get()
                .uri("/atletas/mercado")
                .retrieve()
                .body(AtletaResponse.class);
    }

    @Cacheable(CacheConfig.CACHE_CLUBES)
    public Map<String, ClubeResponse> buscarClubes() {
        log.debug("Buscando clubes do Cartola...");
        return restClient.get()
                .uri("/clubes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @Cacheable(CacheConfig.CACHE_PARTIDAS)
    public PartidaResponse buscarPartidas() {
        log.debug("Buscando partidas da rodada...");
        return restClient.get()
                .uri("/partidas")
                .retrieve()
                .body(PartidaResponse.class);
    }

    /**
     * Busca atletas pontuados da rodada informada.
     * Usado para calcular a media das ultimas 5 rodadas.
     *
     * @param rodada numero da rodada (1-38)
     * @return PontuadosResponse ou null se nao houver dados
     */
    @Cacheable(value = CacheConfig.CACHE_PONTUADOS, key = "#rodada")
    public PontuadosResponse buscarPontuados(int rodada) {
        log.debug("Buscando pontuados da rodada {}...", rodada);
        try {
            return restClient.get()
                    .uri("/atletas/pontuados")
                    .retrieve()
                    .body(PontuadosResponse.class);
        } catch (RestClientException e) {
            log.warn("Nao foi possivel buscar pontuados da rodada {}: {}", rodada, e.getMessage());
            return null;
        }
    }
}
