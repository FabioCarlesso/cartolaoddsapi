package com.cartola.odds.client;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsComOrigem;
import com.cartola.odds.model.OddsCota;
import com.cartola.odds.model.OddsSnapshot;
import com.cartola.odds.model.response.OddsResponse;
import com.cartola.odds.repository.OddsCotaRepository;
import com.cartola.odds.repository.OddsSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
 * expoe o ultimo valor conhecido para {@code GET /api/odds/cota} e as metricas Micrometer
 * ({@code odds_api_requests_total}, {@code odds_api_requests_remaining},
 * {@code odds_api_errors_total}), e para de chamar o provedor quando o saldo cai abaixo de
 * {@code odds.api.min-requests-remaining} — servindo em vez disso a ultima resposta
 * conhecida, persistida em {@code odds_snapshot} para sobreviver a restart e redeploy.
 *
 * <p>Duas valvulas evitam que o guardrail vire uma porta trancada por dentro: uma sondagem
 * periodica ({@code odds.api.sonda-intervalo-horas}), sem a qual o saldo nunca seria
 * reavaliado e a virada de mes que renova a cota passaria despercebida; e o atalho de
 * snapshot valer <em>so na primeira execucao</em> apos o boot, para que
 * {@code DELETE /api/cache} continue sendo o gatilho manual de gasto que sempre foi.
 */
@Slf4j
@Component
public class OddsClient {

    /** Sentinela de "nenhuma leitura de cota ainda" — nao pode ser um saldo real. */
    private static final long SEM_LEITURA = -1L;

    private final RestClient             restClient;
    private final OddsProperties         props;
    private final OddsSnapshotRepository snapshotRepository;
    private final OddsCotaRepository     cotaRepository;
    private final ObjectMapper           objectMapper;

    private final Counter requestsTotal;
    private final Counter errorsTotal;

    private final AtomicLong requestsRemaining = new AtomicLong(SEM_LEITURA);
    private final AtomicLong requestsUsed      = new AtomicLong(SEM_LEITURA);
    private final AtomicReference<LocalDateTime> ultimaLeitura   = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> ultimaSondagem  = new AtomicReference<>();

    /**
     * Atalho de boot: na primeira execucao apos subir, um snapshot ainda dentro do TTL
     * dispensa a chamada ao provedor — o cache Caffeine e zerado a cada restart e redeploy,
     * e redescobrir a mesma resposta custaria credito. Consumido uma unica vez por instancia,
     * de proposito: depois disso, um miss significa TTL vencido ou cache limpo a mao
     * ({@code DELETE /api/cache}), e os dois devem chegar ao provedor.
     */
    private final AtomicBoolean atalhoDeBootDisponivel = new AtomicBoolean(true);

    public OddsClient(@Qualifier("oddsRestClient") RestClient restClient,
                       OddsProperties props,
                       OddsSnapshotRepository snapshotRepository,
                       OddsCotaRepository cotaRepository,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry) {
        this.restClient         = restClient;
        this.props              = props;
        this.snapshotRepository = snapshotRepository;
        this.cotaRepository     = cotaRepository;
        this.objectMapper       = objectMapper;
        this.requestsTotal = meterRegistry.counter("odds_api_requests_total");
        this.errorsTotal   = meterRegistry.counter("odds_api_errors_total");
        // NaN, e nao o sentinela, enquanto nao houve leitura: um -1 exportado faria todo
        // alerta de "saldo < minimo" disparar a cada deploy, antes da primeira chamada.
        // Comparacao com NaN e falsa no PromQL, entao a serie fica silenciosa ate haver dado.
        Gauge.builder("odds_api_requests_remaining", this, OddsClient::saldoParaMetrica)
                .description("Saldo de requisicoes restantes informado pela The Odds API")
                .register(meterRegistry);
    }

    /**
     * Recupera o ultimo estado conhecido da cota. Sem isto o guardrail nasceria desarmado a
     * cada deploy — e e num deploy que o cache em memoria some, ou seja, exatamente quando a
     * proxima requisicao vai querer chamar o provedor.
     *
     * <p>Falha de banco aqui nao impede a aplicacao de subir: o pior caso e comecar sem saldo
     * conhecido, que era o comportamento anterior.
     */
    @PostConstruct
    void carregarCotaPersistida() {
        try {
            cotaRepository.findById(OddsCota.ID_UNICO).ifPresent(cota -> {
                if (cota.getRequestsRemaining() != null) requestsRemaining.set(cota.getRequestsRemaining());
                if (cota.getRequestsUsed() != null)      requestsUsed.set(cota.getRequestsUsed());
                ultimaLeitura.set(cota.getUltimaLeitura());
                ultimaSondagem.set(cota.getUltimaSondagem());
                log.info("Cota da The Odds API recuperada do banco: saldo={} consumo={} ultima leitura={}",
                        cota.getRequestsRemaining(), cota.getRequestsUsed(), cota.getUltimaLeitura());
            });
        } catch (Exception e) {
            log.warn("Nao foi possivel recuperar a cota persistida: {}. Comecando sem saldo conhecido.",
                    e.getMessage());
        }
    }

    /**
     * Busca as odds do Brasileirao, junto com a origem (ao vivo ou snapshot).
     * Resultado cacheado por {@code odds.api.cache-ttl-minutos} (padrao 60 min); uma resposta
     * sem nenhum jogo fica so {@code odds.api.cache-ttl-degradado-minutos} (padrao 10 min).
     *
     * <p>{@code sync = true} porque o custo aqui e dinheiro: sem ele, N misses simultaneos
     * viram N chamadas pagas ao provedor para produzir o mesmo valor.
     */
    @Cacheable(cacheNames = CacheConfig.CACHE_ODDS, sync = true)
    public OddsComOrigem buscarOdds() {
        if ("SUA_API_KEY_AQUI".equals(props.getKey())) {
            log.warn("Odds API Key nao configurada. Rodando sem filtro de favoritos.");
            return OddsComOrigem.indisponivel();
        }

        Optional<OddsSnapshot> snapshot = buscarSnapshot();
        boolean atalhoDeBoot = atalhoDeBootDisponivel.getAndSet(false);

        if (atalhoDeBoot && snapshot.isPresent() && dentroDoTtl(snapshot.get())) {
            log.info("Primeira busca apos o boot com snapshot de odds dentro do TTL ({} min). "
                    + "Evitando uma chamada ao provedor.", props.getCacheTtlMinutos());
            return lerSnapshot(snapshot.get());
        }

        if (guardrailBloqueia()) {
            log.error("Guardrail de cota ativo: saldo restante ({}) abaixo do minimo configurado ({}). "
                            + "Servindo a ultima resposta conhecida sem chamar a The Odds API.",
                    requestsRemaining.get(), props.getMinRequestsRemaining());
            return snapshot.map(this::lerSnapshot).orElseGet(() -> {
                log.error("Guardrail de cota ativo e nenhum snapshot disponivel. Rodando sem filtro de favoritos.");
                return OddsComOrigem.indisponivel();
            });
        }

        return consultarProvedor(snapshot);
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

    // ── Privado ───────────────────────────────────────────────────────

    /**
     * Le o snapshot sem deixar uma falha de banco derrubar a busca. O fallback existe para
     * degradar com elegancia; nao faria sentido ele proprio transformar {@code /api/favoritos}
     * e {@code /api/time} em 500 quando o banco tossir.
     */
    private Optional<OddsSnapshot> buscarSnapshot() {
        try {
            return snapshotRepository.findById(OddsSnapshot.ID_UNICO);
        } catch (Exception e) {
            log.warn("Nao foi possivel ler o snapshot de odds: {}. Seguindo sem fallback.", e.getMessage());
            return Optional.empty();
        }
    }

    private OddsComOrigem consultarProvedor(Optional<OddsSnapshot> snapshot) {
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
            log.info("Odds recebidas: {} jogos (cacheado por {} min)", odds.size(),
                    odds.isEmpty() ? props.getCacheTtlDegradadoMinutos() : props.getCacheTtlMinutos());

            // Resposta sem jogos nao substitui o snapshot: sobrescrever com uma lista vazia
            // destruiria o unico fallback que o guardrail tem para servir depois.
            if (odds.isEmpty()) {
                log.warn("Provedor respondeu sem nenhum jogo. Mantendo o snapshot anterior como fallback.");
            } else {
                persistirSnapshot(odds);
            }
            return OddsComOrigem.aoVivo(odds);

        } catch (RestClientException e) {
            errorsTotal.increment();
            log.error("Erro ao buscar odds: {}.", e.getMessage());
            // A resposta de erro e onde o saldo real aparece quando a cota estoura: a The Odds
            // API recusa a chamada e informa o que sobrou. Ler so no caminho de sucesso deixava
            // o saldo congelado no ultimo valor saudavel — o guardrail nunca armaria justamente
            // no caso em que ele existe para agir.
            if (e instanceof RestClientResponseException erro && erro.getResponseHeaders() != null) {
                registrarCota(erro.getResponseHeaders());
            }
            return snapshot.map(s -> {
                log.warn("Servindo a ultima resposta conhecida de odds apos falha no provedor.");
                return lerSnapshot(s);
            }).orElseGet(() -> {
                log.error("Sem snapshot disponivel apos falha no provedor. Rodando sem filtro de favoritos.");
                return OddsComOrigem.indisponivel();
            });
        }
    }

    /**
     * {@code true} quando o guardrail deve barrar a chamada. Com o saldo abaixo do minimo ele
     * barra, <strong>exceto</strong> quando a ultima noticia da cota ja esta velha o bastante
     * para valer uma sondagem: o saldo so e reavaliado quando uma chamada acontece, entao sem
     * essa valvula o guardrail se auto-alimentaria — barra, o saldo nunca atualiza, continua
     * barrando — e a virada de mes que renova a cota so seria percebida num restart.
     */
    private boolean guardrailBloqueia() {
        if (!isGuardrailAtivo()) return false;

        var referencia = maisRecente(ultimaLeitura.get(), ultimaSondagem.get());
        boolean sondagemVencida = referencia == null
                || referencia.plusHours(props.getSondaIntervaloHoras()).isBefore(LocalDateTime.now());

        if (sondagemVencida) {
            ultimaSondagem.set(LocalDateTime.now());
            persistirCota();
            log.warn("Guardrail de cota ativo (saldo {} < {}), mas a ultima leitura tem mais de {}h: "
                            + "liberando uma chamada de sondagem para reavaliar o saldo.",
                    requestsRemaining.get(), props.getMinRequestsRemaining(), props.getSondaIntervaloHoras());
            return false;
        }
        return true;
    }

    private static LocalDateTime maisRecente(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static double saldoParaMetrica(OddsClient client) {
        long valor = client.requestsRemaining.get();
        return valor == SEM_LEITURA ? Double.NaN : valor;
    }

    private void registrarCota(HttpHeaders headers) {
        Long remaining = parseHeader(headers, "x-requests-remaining");
        Long used      = parseHeader(headers, "x-requests-used");

        // Sem nenhum header, nada foi aprendido — e marcar a leitura assim mesmo empurraria a
        // proxima sondagem por um intervalo inteiro em troca de nada, mantendo o guardrail
        // barrando com um saldo que ninguem conferiu.
        if (remaining == null && used == null) {
            log.warn("Resposta da The Odds API sem os headers de cota. "
                    + "O saldo conhecido segue sendo o da ultima leitura.");
            return;
        }

        if (remaining != null) {
            long anterior = requestsRemaining.getAndSet(remaining);
            avisarSeCruzouLimiar(anterior, remaining);
        }
        if (used != null) {
            requestsUsed.set(used);
        }
        ultimaLeitura.set(LocalDateTime.now());
        persistirCota();
    }

    /**
     * Grava o estado da cota para ele sobreviver ao restart. Falha aqui nao interrompe a busca:
     * o valor em memoria continua valendo para esta instancia, e o pior caso e um deploy futuro
     * comecar sem saldo conhecido.
     */
    private void persistirCota() {
        try {
            var cota = cotaRepository.findById(OddsCota.ID_UNICO).orElseGet(OddsCota::new);
            cota.setId(OddsCota.ID_UNICO);
            cota.setRequestsRemaining(getRequestsRemaining());
            cota.setRequestsUsed(getRequestsUsed());
            cota.setUltimaLeitura(ultimaLeitura.get());
            cota.setUltimaSondagem(ultimaSondagem.get());
            cotaRepository.save(cota);
        } catch (Exception e) {
            log.warn("Nao foi possivel persistir a cota da The Odds API: {}", e.getMessage());
        }
    }

    /**
     * Avisa ao cruzar o dobro do minimo configurado (aviso cedo) e o proprio minimo (o corte
     * do guardrail). Os limiares saem da configuracao, e nao de constantes: fixos em 100/50,
     * um {@code min-requests-remaining=200} acionaria o guardrail sem nenhum aviso previo.
     * Com o padrao de 50, os limiares continuam sendo 100 e 50.
     */
    private void avisarSeCruzouLimiar(long anterior, long atual) {
        long critico = props.getMinRequestsRemaining();
        long alerta  = critico * 2L;
        boolean semLeituraAnterior = anterior == SEM_LEITURA;

        if (atual <= critico && (semLeituraAnterior || anterior > critico)) {
            log.warn("Cota da The Odds API cruzou {} requisicoes restantes: saldo atual = {}", critico, atual);
        } else if (atual <= alerta && (semLeituraAnterior || anterior > alerta)) {
            log.warn("Cota da The Odds API cruzou {} requisicoes restantes: saldo atual = {}", alerta, atual);
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

    private OddsComOrigem lerSnapshot(OddsSnapshot snapshot) {
        try {
            List<OddsResponse> odds = objectMapper.readValue(
                    snapshot.getOddsJson(), new TypeReference<List<OddsResponse>>() {});
            log.info("Servindo {} jogos do snapshot de odds persistido em {}.", odds.size(), snapshot.getCriadoEm());
            // O instante de origem e o do snapshot, nao o de agora: e o que faz o cache guardar
            // so o tempo que resta do TTL em vez de comecar a contar de novo.
            return OddsComOrigem.deSnapshot(odds, snapshot.getCriadoEm());
        } catch (Exception e) {
            log.error("Snapshot de odds persistido esta corrompido: {}. Rodando sem filtro de favoritos.",
                    e.getMessage());
            // Degradado, e nao "ao vivo": a resposta continua sendo o que sobrou de uma
            // tentativa de fallback, e o payload nao deve chamar isso de consulta ao provedor.
            return OddsComOrigem.deSnapshot(List.of());
        }
    }

    private boolean dentroDoTtl(OddsSnapshot snapshot) {
        var limite = snapshot.getCriadoEm().plusMinutes(props.getCacheTtlMinutos());
        return LocalDateTime.now().isBefore(limite);
    }
}
