package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.model.response.FavoritosResponse;
import com.cartola.odds.model.response.FavoritosResponse.JogoDescartadoDto;
import com.cartola.odds.model.response.FavoritosResponse.JogoFavoritoDto;
import com.cartola.odds.model.response.OddsResponse;
import com.cartola.odds.util.NormalizadorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OddsService {

    private final OddsClient           oddsClient;
    private final ConfiguracaoService  configuracaoService;
    private final CartolaDataService   cartolaDataService;

    // ── API interna (usada pelo pipeline de time/ranking) ─────────────

    public Set<String> buscarFavoritos() {
        return buscarFavoritos(configuracaoService.buscarConfig().getOddLimite(), buscarConfrontosComFallback());
    }

    public Set<String> buscarFavoritos(Set<String> confrontos) {
        return buscarFavoritos(configuracaoService.buscarConfig().getOddLimite(), confrontos);
    }

    public Set<String> buscarFavoritos(double oddLimite) {
        return buscarFavoritos(oddLimite, buscarConfrontosComFallback());
    }

    public Set<String> buscarFavoritos(double oddLimite, Set<String> confrontos) {
        var response = processarOdds(oddsClient.buscarOdds(), oddLimite, confrontos);
        return response.getFavoritos().stream()
                .map(j -> NormalizadorUtil.normalizar(j.getTimeFavorito()))
                .collect(Collectors.toUnmodifiableSet());
    }

    // ── API publica (usada pelo endpoint de favoritos) ─────────────────

    public FavoritosResponse buscarFavoritosDetalhado(double oddLimite) {
        log.info("Buscando favoritos detalhado | oddLimite={}", oddLimite);
        List<OddsResponse> odds = oddsClient.buscarOdds();

        if (odds.isEmpty()) {
            log.warn("Nenhuma odd disponivel da API.");
            return FavoritosResponse.builder()
                    .oddLimite(oddLimite)
                    .totalJogos(0)
                    .totalFavoritos(0)
                    .totalDescartados(0)
                    .favoritos(List.of())
                    .descartados(List.of())
                    .build();
        }

        return processarOdds(odds, oddLimite, buscarConfrontosComFallback());
    }

    // ── Privado ───────────────────────────────────────────────────────

    private FavoritosResponse processarOdds(List<OddsResponse> odds, double oddLimite, Set<String> confrontosRodadaAtual) {
        List<JogoFavoritoDto>   favoritos   = new ArrayList<>();
        List<JogoDescartadoDto> descartados = new ArrayList<>();
        List<OddsResponse> oddsRodadaAtual = filtrarOddsRodadaAtual(odds, confrontosRodadaAtual);

        for (OddsResponse jogo : oddsRodadaAtual) {
            processarJogo(jogo, oddLimite, favoritos, descartados);
        }

        log.info("Favoritos: {} | Descartados: {} | Total jogos: {}",
                favoritos.size(), descartados.size(), oddsRodadaAtual.size());

        return FavoritosResponse.builder()
                .oddLimite(oddLimite)
                .totalJogos(oddsRodadaAtual.size())
                .totalFavoritos(favoritos.size())
                .totalDescartados(descartados.size())
                .favoritos(favoritos)
                .descartados(descartados)
                .build();
    }

    private Set<String> buscarConfrontosComFallback() {
        Set<String> confrontos;
        try {
            confrontos = cartolaDataService.buscarConfrontosRodadaAtual();
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel buscar confrontos da rodada atual: {}. Processando todas as odds disponiveis.",
                    e.getMessage());
            return Set.of();
        }
        if (confrontos.isEmpty()) {
            log.warn("Nao foi possivel identificar confrontos da rodada atual. Processando todas as odds disponiveis.");
            return Set.of();
        }
        return confrontos;
    }

    private List<OddsResponse> filtrarOddsRodadaAtual(List<OddsResponse> odds, Set<String> confrontosRodadaAtual) {
        if (confrontosRodadaAtual.isEmpty()) return odds;

        List<OddsResponse> filtradas = odds.stream()
                .filter(jogo -> confrontosRodadaAtual.contains(
                        NormalizadorUtil.chaveConfronto(jogo.getHomeTeam(), jogo.getAwayTeam())))
                .toList();

        log.info("Odds filtradas pela rodada atual: {} de {} jogos", filtradas.size(), odds.size());

        // Descartar tudo nao e filtragem normal: significa que os dois lados nao se reconhecem
        // mais. E uma falha silenciosa — nao lanca, so devolve lista vazia —, e foi assim que a
        // mudanca de contrato do Cartola (#45) rodou despercebida. O par de chaves no log e o que
        // torna o diagnostico imediato, sem precisar reproduzir as duas chamadas na mao.
        if (filtradas.isEmpty() && !odds.isEmpty()) {
            log.warn("Nenhuma das {} odds casou com os {} confrontos da rodada — provavel divergencia "
                            + "de nomes entre as fontes. Exemplo: odds={} | confrontos={}",
                    odds.size(), confrontosRodadaAtual.size(),
                    NormalizadorUtil.chaveConfronto(odds.get(0).getHomeTeam(), odds.get(0).getAwayTeam()),
                    confrontosRodadaAtual.iterator().next());
        }

        return filtradas;
    }

    private void processarJogo(OddsResponse jogo, double oddLimite,
                                List<JogoFavoritoDto>   favoritos,
                                List<JogoDescartadoDto> descartados) {

        if (jogo.getBookmakers() == null || jogo.getBookmakers().isEmpty()) return;

        var mercado = jogo.getBookmakers().get(0).getMarkets();
        if (mercado == null || mercado.isEmpty()) return;

        var outcomes = mercado.get(0).getOutcomes();
        if (outcomes == null || outcomes.isEmpty()) return;

        String homeName = jogo.getHomeTeam() != null ? jogo.getHomeTeam() : "";
        String awayName = jogo.getAwayTeam() != null ? jogo.getAwayTeam() : "";

        double oddCasa      = findOdd(outcomes, homeName);
        double oddVisitante = findOdd(outcomes, awayName);
        double oddEmpate    = findOdd(outcomes, "Draw");

        Optional<OddsResponse.Outcome> menorOdd = outcomes.stream()
                .filter(o -> !"Draw".equalsIgnoreCase(o.getName()))
                .min(Comparator.comparingDouble(OddsResponse.Outcome::getPrice));

        if (menorOdd.isEmpty()) return;

        double  menorOddValor  = menorOdd.get().getPrice();
        String  nomeFavorito   = menorOdd.get().getName();
        boolean emCasa         = nomeFavorito.equalsIgnoreCase(homeName);
        String  nomeAdversario = emCasa ? awayName : homeName;
        double  oddAdversario  = emCasa ? oddVisitante : oddCasa;

        if (menorOddValor <= oddLimite) {
            favoritos.add(JogoFavoritoDto.builder()
                    .timeFavorito(nomeFavorito)
                    .oddFavorito(menorOddValor)
                    .timeAdversario(nomeAdversario)
                    .oddAdversario(oddAdversario)
                    .oddEmpate(oddEmpate)
                    .favoritoEmCasa(emCasa)
                    .build());
            log.info("Favorito: {} odd={} vs {} | em casa={}", nomeFavorito, menorOddValor, nomeAdversario, emCasa);
        } else {
            descartados.add(JogoDescartadoDto.builder()
                    .timeCasa(homeName)
                    .oddCasa(oddCasa)
                    .timeVisitante(awayName)
                    .oddVisitante(oddVisitante)
                    .oddEmpate(oddEmpate)
                    .motivo(String.format(java.util.Locale.ROOT, "Menor odd (%.2f) acima do limite (%.1f)", menorOddValor, oddLimite))
                    .build());
            log.debug("Descartado: {} odd={} > {}", nomeFavorito, menorOddValor, oddLimite);
        }
    }

    private double findOdd(List<OddsResponse.Outcome> outcomes, String name) {
        return outcomes.stream()
                .filter(o -> o.getName() != null && o.getName().equalsIgnoreCase(name))
                .mapToDouble(OddsResponse.Outcome::getPrice)
                .findFirst()
                .orElse(0.0);
    }
}
