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

    public Set<Integer> buscarTimesCasa() {
        return extrairTimesCasa(cartolaClient.buscarPartidas());
    }

    // ── Privados ──────────────────────────────────────────────────────

    private Set<Integer> extrairTimesCasa(PartidaResponse partidas) {
        if (partidas == null || partidas.getPartidas() == null) return Set.of();
        return partidas.getPartidas().stream()
                .map(PartidaResponse.PartidaItem::getClubeCasaId)
                .collect(Collectors.toSet());
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
        String nomeClubeNorm  = NormalizadorUtil.normalizar(nomeClube);

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
                .desempenhoRecente(0.0)  // preenchido pelo ScoreService
                .score(0.0)
                .build();
    }
}
