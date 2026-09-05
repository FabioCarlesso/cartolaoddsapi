package com.cartola.odds.client;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsSnapshot;
import com.cartola.odds.model.response.OddsResponse;
import com.cartola.odds.repository.OddsSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cliente da The Odds API com guardrail de cota (#40).
 *
 * <p>A The Odds API devolve o saldo restante em cada resposta, nos headers
 * {@code x-requests-remaining} e {@code x-requests-used}. Este cliente le esses headers,
 * expõe o ultimo valor conhecido para {@code GET /api/odds/cota} e as metricas Micrometer
 * ({@code odds_api_requests_total}, {@code odds_api_requests_remaining},
 * {@code odds_api_errors_total}), e para de chamar o provedor quando o saldo cai abaixo de
 * {@code odds.api.min-requests-remaining} — servindo em vez disso a ultima resposta
 * conhecida, persistida em {@code odds_snapshot} para sobreviver a restart e redeploy.
 */
@Slf4j
@Component
public class OddsClient {

    private static final String LIMIAR_ALERTA_LOG  = "100";
    private static final String LIMIAR_CRITICO_LOG = "50";
    private static final long LIMIAR_ALERTA  = 100;
    private static final long LIMIAR_CRITICO = 50;

    /** Sentinela de "nenhuma leitura de cota ainda" — nao pode ser um saldo real. */
    private static final long SEM_LEITURA = -1L;

    private final RestClient             restClient;
    private final OddsProperties         props;
    private final OddsSnapshotRepository snapshotRepository;
    private final ObjectMapper           objectMapper;

    private final Counter requestsTotal;
    private final Counter errorsTotal;

    private final AtomicLong requestsRemaining = new AtomicLong(SEM_LEITURA);
    private final AtomicLong requestsUsed      = new AtomicLong(SEM_LEITURA);
    private final AtomicReference<LocalDateTime> ultimaLeitura = new AtomicReference<>();

    /** Reflete se a ultima chamada a {@link #buscarOdds()} serviu o snapshot persistido em vez de consultar o provedor. */
    private final AtomicBoolean vindoDeSnapshot = new AtomicBoolean(false);

    public OddsClient(@Qualifier("oddsRestClient") RestClient restClient,
                       OddsProperties props,
                       OddsSnapshotRepository snapshotRepository,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry) {
        this.restClient         = restClient;
        this.props              = props;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper       = objectMapper;
        this.requestsTotal = meterRegistry.counter("odds_api_requests_total");
        this.errorsTotal   = meterRegistry.counter("odds_api_errors_total");
        meterRegistry.gauge("odds_api_requests_remaining", requestsRemaining);
    }

    /**
     * Busca as odds do Brasileirao.
     * Resultado cacheado por {@code odds.api.cache-ttl-minutos} (padrao 60 min).
     *
     * <p>Antes de chamar o provedor: usa o snapshot persistido sem gastar cota quando ele
     * ainda esta dentro do TTL do cache (cobre o caso de restart com cache Caffeine vazio),
     * e para de chamar o provedor quando o guardrail de cota esta ativo. Em qualquer falha do
     * provedor, tambem recai no snapshot antes de desistir com lista vazia.
     */
    @Cacheable(CacheConfig.CACHE_ODDS)
    public List<OddsResponse> buscarOdds() {
        vindoDeSnapshot.set(false);

        if ("SUA_API_KEY_AQUI".equals(props.getKey())) {
            log.warn("Odds API Key nao configurada. Rodando sem filtro de favoritos.");
            return Collections.emptyList();
        }

        Optional<OddsSnapshot> snapshot = snapshotRepository.findById(OddsSnapshot.ID_UNICO);

        if (snapshot.isPresent() && dentroDoTtl(snapshot.get())) {
            log.debug("Snapshot de odds dentro do TTL ({} min). Evitando chamada ao provedor.",
                    props.getCacheTtlMinutos());
            return usarSnapshot(snapshot.get());
        }

        long remanescente = requestsRemaining.get();
        if (remanescente != SEM_LEITURA && remanescente < props.getMinRequestsRemaining()) {
            log.error("Guardrail de cota ativo: saldo restante ({}) abaixo do minimo configurado ({}). "
                            + "Servindo a ultima resposta conhecida sem chamar a The Odds API.",
                    remanescente, props.getMinRequestsRemaining());
            return snapshot.map(this::usarSnapshot).orElseGet(() -> {
                log.error("Guardrail de cota ativo e nenhum snapshot disponivel. Rodando sem filtro de favoritos.");
                return Collections.emptyList();
            });
        }

        try {
            var uri = "/sports/%s/odds".formatted(props.getSport());
            log.debug("Buscando odds: {}", uri);

            var resposta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(uri)
                            .queryParam("regions", props.getRegions())
                            .queryParam("markets", props.getMarkets())
                            .queryParam("apiKey", props.getKey())
                            .build())
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<OddsResponse>>() {});

            requestsTotal.increment();
            registrarCota(resposta.getHeaders());

            List<OddsResponse> odds = resposta.getBody() != null ? resposta.getBody() : Collections.emptyList();
            log.info("Odds recebidas: {} jogos (cacheado por {} min)", odds.size(), props.getCacheTtlMinutos());

            persistirSnapshot(odds);
            return odds;

        } catch (RestClientException e) {
            errorsTotal.increment();
            log.error("Erro ao buscar odds: {}.", e.getMessage());
            return snapshot.map(s -> {
                log.warn("Servindo a ultima resposta conhecida de odds apos falha no provedor.");
                return usarSnapshot(s);
            }).orElseGet(() -> {
                log.error("Sem snapshot disponivel apos falha no provedor. Rodando sem filtro de favoritos.");
                return Collections.emptyList();
            });
        }
    }

    /** Ultimo saldo de requisicoes restantes lido do provedor, ou {@code null} sem leitura ainda. */
    public Long getRequestsRemaining() {
        long valor = requestsRemaining.get();
        return valor == SEM_LEITURA ? null : valor;
    }

    /** Ultimo consumo do mes lido do provedor, ou {@code null} sem leitura ainda. */
    public Long getRequestsUsed() {
        long valor = requestsUsed.get();
        return valor == SEM_LEITURA ? null : valor;
    }

    /** Instante da ultima leitura de cota, ou {@code null} sem leitura ainda. */
    public LocalDateTime getUltimaLeitura() {
        return ultimaLeitura.get();
    }

    /** {@code true} quando o saldo conhecido esta abaixo do minimo configurado. */
    public boolean isGuardrailAtivo() {
        long valor = requestsRemaining.get();
        return valor != SEM_LEITURA && valor < props.getMinRequestsRemaining();
    }

    /** {@code true} quando a ultima chamada a {@link #buscarOdds()} serviu o snapshot persistido. */
    public boolean isVindoDeSnapshot() {
        return vindoDeSnapshot.get();
    }

    private void registrarCota(HttpHeaders headers) {
        Long remaining = parseHeader(headers, "x-requests-remaining");
        Long used      = parseHeader(headers, "x-requests-used");

        if (remaining != null) {
            long anterior = requestsRemaining.getAndSet(remaining);
            avisarSeCruzouLimiar(anterior, remaining);
        }
        if (used != null) {
            requestsUsed.set(used);
        }
        ultimaLeitura.set(LocalDateTime.now());
    }

    private void avisarSeCruzouLimiar(long anterior, long atual) {
        boolean semLeituraAnterior = anterior == SEM_LEITURA;
        if (atual <= LIMIAR_CRITICO && (semLeituraAnterior || anterior > LIMIAR_CRITICO)) {
            log.warn("Cota da The Odds API cruzou {} requisicoes restantes: saldo atual = {}",
                    LIMIAR_CRITICO_LOG, atual);
        } else if (atual <= LIMIAR_ALERTA && (semLeituraAnterior || anterior > LIMIAR_ALERTA)) {
            log.warn("Cota da The Odds API cruzou {} requisicoes restantes: saldo atual = {}",
                    LIMIAR_ALERTA_LOG, atual);
        }
    }

    private Long parseHeader(HttpHeaders headers, String nome) {
        String valor = headers.getFirst(nome);
        if (valor == null) return null;
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            log.warn("Header '{}' da The Odds API com valor invalido: '{}'", nome, valor);
            return null;
        }
    }

    private void persistirSnapshot(List<OddsResponse> odds) {
        try {
            var snapshot = snapshotRepository.findById(OddsSnapshot.ID_UNICO).orElseGet(OddsSnapshot::new);
            snapshot.setId(OddsSnapshot.ID_UNICO);
            snapshot.setOddsJson(objectMapper.writeValueAsString(odds));
            snapshot.setCriadoEm(LocalDateTime.now());
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("Nao foi possivel persistir o snapshot de odds: {}", e.getMessage());
        }
    }

    private List<OddsResponse> usarSnapshot(OddsSnapshot snapshot) {
        vindoDeSnapshot.set(true);
        try {
            List<OddsResponse> odds = objectMapper.readValue(
                    snapshot.getOddsJson(), new TypeReference<List<OddsResponse>>() {});
            log.info("Servindo {} jogos do snapshot de odds persistido em {}.", odds.size(), snapshot.getCriadoEm());
            return odds;
        } catch (Exception e) {
            log.error("Snapshot de odds persistido esta corrompido: {}. Rodando sem filtro de favoritos.",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean dentroDoTtl(OddsSnapshot snapshot) {
        var limite = snapshot.getCriadoEm().plusMinutes(props.getCacheTtlMinutos());
        return LocalDateTime.now().isBefore(limite);
    }
}
