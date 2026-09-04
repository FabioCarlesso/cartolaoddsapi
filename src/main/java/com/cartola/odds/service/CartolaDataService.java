package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.AtletaResponse;
import com.cartola.odds.model.response.ClubeResponse;
import com.cartola.odds.model.response.MercadoStatusResponse;
import com.cartola.odds.model.response.PartidaResponse;
import com.cartola.odds.util.NormalizadorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartolaDataService {

    private final CartolaClient cartolaClient;

    public MercadoStatusResponse buscarStatusMercado() {
        var status = cartolaClient.buscarStatusMercado();
        log.info("Rodada: {} | Mercado: {}",
                status.getRodadaAtual(),
                status.isAberto() ? "Aberto" : "Fechado");
        return status;
    }

    public List<Atleta> buscarAtletasFiltrados(Set<String> favoritos) {
        Map<String, ClubeResponse> clubesRaw = cartolaClient.buscarClubes();
        AtletaResponse   atletasRaw          = cartolaClient.buscarAtletas();
        PartidaResponse  partidasRaw         = cartolaClient.buscarPartidas();

        log.info("Atletas carregados: {} | Clubes: {}",
                atletasRaw.getAtletas().size(), clubesRaw.size());

        Set<Integer> timesCasa = extrairTimesCasa(partidasRaw);
        log.info("Times mandantes na rodada: {}", timesCasa.size());

        var atletas = atletasRaw.getAtletas().stream()
                .map(a -> mapearAtleta(a, clubesRaw, timesCasa, favoritos))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long provaveis = atletas.stream().filter(Atleta::isProvavel).count();
        long duvidas   = atletas.stream().filter(Atleta::isDuvida).count();
        log.info("Pool final: Provaveis={} | Duvida={} | Total={}", provaveis, duvidas, atletas.size());

        return atletas;
    }

    public record DadosRodada(Set<Integer> timesCasa, Set<String> confrontos) {}

    public DadosRodada buscarDadosRodada() {
        Map<String, ClubeResponse> clubes = cartolaClient.buscarClubes();
        PartidaResponse partidas = cartolaClient.buscarPartidas();
        return new DadosRodada(extrairTimesCasa(partidas), extrairConfrontos(partidas, clubes));
    }

    public Set<Integer> buscarTimesCasa() {
        return extrairTimesCasa(cartolaClient.buscarPartidas());
    }

    public Set<String> buscarConfrontosRodadaAtual() {
        return buscarDadosRodada().confrontos();
    }

    // ── Privados ──────────────────────────────────────────────────────

    private Set<Integer> extrairTimesCasa(PartidaResponse partidas) {
        if (partidas == null || partidas.getPartidas() == null) return Set.of();
        return partidas.getPartidas().stream()
                .map(PartidaResponse.PartidaItem::getClubeCasaId)
                .collect(Collectors.toSet());
    }

    private Set<String> extrairConfrontos(PartidaResponse partidas, Map<String, ClubeResponse> clubes) {
        if (partidas == null || partidas.getPartidas() == null || clubes == null) return Set.of();
        return partidas.getPartidas().stream()
                .map(p -> NormalizadorUtil.chaveConfronto(
                        nomeClubeParaChave(clubes.get(String.valueOf(p.getClubeCasaId()))),
                        nomeClubeParaChave(clubes.get(String.valueOf(p.getClubeVisitanteId())))
                ))
                .filter(chave -> !chave.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Nome de exibicao do clube — e o que sai em {@code nomeClube} no ranking e no time. */
    private String nomeClube(ClubeResponse clube) {
        if (clube == null) return "";
        if (clube.getNomeFantasia() != null && !clube.getNomeFantasia().isBlank()) return clube.getNomeFantasia();
        return clube.getNome() != null ? clube.getNome() : "";
    }

    /**
     * Nome usado para cruzar o clube com a The Odds API — na chave de confronto da rodada e
     * no {@code nomeClubeNorm} do atleta.
     *
     * <p>Separado do {@link #nomeClube(ClubeResponse)} de proposito: os dois partiam do mesmo
     * campo ate o {@code /partidas} do Cartola passar a devolver sigla em {@code nome} e
     * {@code nome_fantasia}. Com "MIR" dos dois lados, a chave virava {@code bah|rbb} enquanto
     * a da Odds API era {@code bahia|bragantino}, e nenhum jogo casava. O {@code slug} e o unico
     * campo que ainda traz o nome por extenso.
     *
     * <p>Unificar os dois de novo custaria a exibicao: o slug apareceria como "athletico-pr" na
     * tela, onde hoje sai "CAP". O fallback para os campos antigos fica para o caso de a API
     * voltar ao formato anterior.
     */
    private String nomeClubeParaChave(ClubeResponse clube) {
        if (clube == null) return "";
        if (clube.getSlug() != null && !clube.getSlug().isBlank()) return clube.getSlug();
        return nomeClube(clube);
    }

    private Atleta mapearAtleta(AtletaResponse.AtletaItem item,
                                 Map<String, ClubeResponse> clubes,
                                 Set<Integer> timesCasa,
                                 Set<String> favoritos) {
        var posicaoOpt = Posicao.fromId(item.getPosicaoId());
        if (posicaoOpt.isEmpty()) return null;

        var statusOpt = StatusAtleta.fromId(item.getStatusId());
        if (statusOpt.isEmpty() || !statusOpt.get().isEscalavel()) return null;

        double preco = item.getPrecoNum() != null ? item.getPrecoNum() : 0.0;
        if (preco <= 0) return null;

        ClubeResponse clube   = clubes.get(String.valueOf(item.getClubeId()));
        String nomeClube      = clube != null && clube.getNomeFantasia() != null
                ? clube.getNomeFantasia()
                : (clube != null ? clube.getNome() : "");
        String siglaClube     = clube != null && clube.getAbreviacao() != null
                ? clube.getAbreviacao()
                : nomeClube.length() >= 3 ? nomeClube.substring(0, 3).toUpperCase() : "???";
        String nomeClubeNorm  = NormalizadorUtil.normalizar(nomeClubeParaChave(clube));

        if (!favoritos.isEmpty() && !favoritos.contains(nomeClubeNorm)) return null;

        return Atleta.builder()
                .atletaId(item.getAtletaId())
                .apelido(item.getApelido() != null ? item.getApelido() : "N/A")
                .posicao(posicaoOpt.get())
                .clubeId(item.getClubeId())
                .nomeClube(nomeClube)
                .siglaClube(siglaClube)
                .nomeClubeNorm(nomeClubeNorm)
                .status(statusOpt.get())
                .mediaPontos(item.getMediaNum() != null ? item.getMediaNum() : 0.0)
                .valorizacao(item.getVariacaoNum() != null ? item.getVariacaoNum() : 0.0)
                .preco(preco)
                .defesasDificeis(getScout(item, "DD"))
                .golsSofridos(getScout(item, "GS"))
                .penaltisDefendidos(getScout(item, "DP"))
                .gols(getScout(item, "G"))
                .assistencias(getScout(item, "A"))
                .desempenhoRecente(0.0)  // preenchido pelo ScoreService
                .score(0.0)
                .build();
    }

    private int getScout(AtletaResponse.AtletaItem item, String key) {
        if (item.getScout() == null) return 0;
        Integer value = item.getScout().get(key);
        return value != null ? value : 0;
    }
}
