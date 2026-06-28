package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.FormacaoConfig;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Estrategia;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MontadorTimeService {

    private static final List<Posicao> PRIORIDADE_CAPITAO =
            List.of(Posicao.ATA, Posicao.MEI, Posicao.ZAG, Posicao.LAT, Posicao.GOL, Posicao.TEC);

    private static final Set<Posicao> POSICOES_DEFESA = Set.of(Posicao.GOL, Posicao.LAT, Posicao.ZAG);
    private static final Set<Posicao> POSICOES_COM_RESERVA =
            Set.of(Posicao.GOL, Posicao.LAT, Posicao.ZAG, Posicao.MEI, Posicao.ATA);

    private final ConfiguracaoService configuracaoService;

    public Time montar(List<Atleta> pool, int rodada, String avisoMercado) {
        return montar(pool, rodada, avisoMercado, null, null);
    }

    public Time montar(List<Atleta> pool, int rodada, String avisoMercado, Double orcamento) {
        return montar(pool, rodada, avisoMercado, orcamento, null);
    }

    /**
     * Monta o time podendo sobrescrever a formacao da configuracao apenas para
     * esta execucao. Quando {@code formacaoOverride} e {@code null}, usa a
     * formacao persistida na configuracao; caso contrario, mantem GOL/TEC da
     * config e deriva a defesa do override (LAT fixo em
     * {@link FormacaoConfig#LATERAIS} e ZAG = defensores - LAT), alem de MEI/ATA.
     *
     * @param formacaoOverride formacao a aplicar nesta execucao (nao altera a config persistida)
     */
    public Time montar(List<Atleta> pool, int rodada, String avisoMercado, Double orcamento,
                       FormacaoConfig formacaoOverride) {
        Configuracao config = configuracaoService.buscarConfig();
        Map<String, Integer> formacao = resolverFormacao(config, formacaoOverride);

        // A montagem usa sempre SCORE_MAXIMO: maximiza a soma de score. Com orcamento,
        // o objetivo passa a ser "score maximo sujeito ao teto", com custo-beneficio
        // (score/preco) apenas como criterio de desempate.
        Estrategia estrategia = Estrategia.SCORE_MAXIMO;

        Map<Posicao, List<Atleta>> porPosicao = pool.stream()
                .collect(Collectors.groupingBy(Atleta::getPosicao));
        Map<Posicao, List<Atleta>> candidatosPorPosicao = new EnumMap<>(Posicao.class);
        porPosicao.forEach((posicao, atletas) -> candidatosPorPosicao.put(
                posicao,
                atletas.stream()
                        .sorted(Comparator.comparingDouble(Atleta::getScore).reversed())
                        .toList()
        ));

        // Budget efetivo: orcamento da requisicao tem prioridade; senao usa budgetMaximo
        // da configuracao. Double.MAX_VALUE quando nao ha restricao (budgetMaximo == 0).
        double budgetEfetivo = orcamento != null
                ? orcamento
                : (config.getBudgetMaximo() > 0 ? config.getBudgetMaximo() : Double.MAX_VALUE);

        Map<Posicao, List<Atleta>> titulares = montarTitulares(
                formacao, candidatosPorPosicao, config, budgetEfetivo);
        Map<Posicao, Atleta> reservas = selecionarReservas(titulares, candidatosPorPosicao);

        Set<String> apelidosTitulares = titulares.values().stream()
                .flatMap(List::stream)
                .map(Atleta::getApelido)
                .collect(Collectors.toSet());

        Map<Posicao, List<Atleta>> provavelPorPosicao = pool.stream()
                .filter(Atleta::isProvavel)
                .filter(a -> !apelidosTitulares.contains(a.getApelido()))
                .collect(Collectors.groupingBy(Atleta::getPosicao));

        Map<Posicao, List<Atleta>> titularesEnriquecidos = new EnumMap<>(Posicao.class);
        List<String> alertas = new ArrayList<>();

        for (Map.Entry<Posicao, List<Atleta>> entry : titulares.entrySet()) {
            List<Atleta> enriquecidos = entry.getValue().stream()
                    .map(j -> {
                        if (!j.isDuvida()) return j;

                        Optional<Atleta> sub = provavelPorPosicao
                                .getOrDefault(j.getPosicao(), List.of())
                                .stream()
                                .max(Comparator.comparingDouble(Atleta::getScore));

                        Atleta comSub = j.withSubstitutoProvavel(sub.orElse(null));

                        String alerta = sub.map(s ->
                                "* %s (%s) [%s] -> Substituto: %s (%s)  C$%.1f  Score: %.4f"
                                        .formatted(j.getApelido(), j.getSiglaClube(), j.getPosicao(),
                                                   s.getApelido(), s.getSiglaClube(),
                                                   s.getPreco(), s.getScore())
                        ).orElse(
                                "* %s (%s) [%s] -> Sem substituto provavel disponivel"
                                        .formatted(j.getApelido(), j.getSiglaClube(), j.getPosicao())
                        );
                        alertas.add(alerta);
                        log.warn("DUVIDA: {}", alerta);
                        return comSub;
                    })
                    .toList();
            titularesEnriquecidos.put(entry.getKey(), enriquecidos);
        }

        List<Atleta> todosTitulares = PRIORIDADE_CAPITAO.stream()
                .flatMap(pos -> titularesEnriquecidos.getOrDefault(pos, List.of()).stream())
                .toList();

        Atleta capitao = todosTitulares.stream()
                .max(Comparator.comparingDouble(Atleta::getScore))
                .orElse(null);

        Atleta reservaLuxo = reservas.values().stream()
                .max(Comparator.comparingDouble(Atleta::getScore))
                .orElse(null);

        if (capitao    != null) log.info("Capitao: {}",      capitao.formatado());
        if (reservaLuxo != null) log.info("Reserva de Luxo: {}", reservaLuxo.formatado());

        double custoTotal = titularesEnriquecidos.values().stream()
                .flatMap(List::stream)
                .mapToDouble(Atleta::getPreco)
                .sum();

        log.info("Custo total dos titulares: C${} | Estrategia: {}",
                String.format("%.1f", custoTotal), estrategia);

        Double saldoRestante = orcamento != null ? orcamento - custoTotal : null;

        int totalEsperado = formacao.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int totalEscalado = (int) titularesEnriquecidos.values().stream()
                .flatMap(List::stream)
                .count();
        boolean formacaoCompleta = totalEscalado >= totalEsperado;

        String avisoOrcamento = null;
        if (orcamento != null && !formacaoCompleta) {
            avisoOrcamento = ("Orcamento de C$%.1f insuficiente para completar a formacao "
                    + "(%d/%d titulares escalados). Considere aumentar o orcamento.")
                    .formatted(orcamento, totalEscalado, totalEsperado);
            log.warn("ORCAMENTO: {}", avisoOrcamento);
        }

        return Time.builder()
                .rodada(rodada)
                .avisoMercado(avisoMercado)
                .titulares(titularesEnriquecidos)
                .reservas(reservas)
                .capitao(capitao)
                .reservaLuxo(reservaLuxo)
                .alertasDuvida(alertas)
                .custoTotal(custoTotal)
                .orcamentoInformado(orcamento)
                .saldoRestante(saldoRestante)
                .estrategia(estrategia)
                .formacaoCompleta(formacaoCompleta)
                .avisoOrcamento(avisoOrcamento)
                .build();
    }

    /**
     * Resolve a formacao usada na montagem. Sem override, usa a formacao da
     * configuracao. Com override, mantem as posicoes fixas da config
     * (GOL/TEC) e deriva a defesa a partir do total de defensores do override:
     * LAT fixo ({@link FormacaoConfig#LATERAIS}) e ZAG = defensores - LAT. Isso
     * alinha a composicao do {@code comparar} com a do {@code GET /api/time}
     * para a mesma string de formacao, preservando a ordem (GOL, LAT, ZAG, MEI,
     * ATA, TEC) exigida pelo calculo de custo minimo.
     *
     * <p>O LAT do override usa a constante {@link FormacaoConfig#LATERAIS} (2),
     * e nao {@code config.getFormacaoLat()}, por ser a quantidade de laterais do
     * Cartola FC. Caso a config seja personalizada com outro valor de LAT, o
     * {@code GET /api/time} (que usa a config) divergiria do {@code comparar}.
     */
    private Map<String, Integer> resolverFormacao(Configuracao config, FormacaoConfig override) {
        if (override == null) {
            return config.getFormacaoAsMap();
        }
        Map<String, Integer> formacao = new LinkedHashMap<>();
        formacao.put("GOL", config.getFormacaoGol());
        formacao.put("LAT", override.laterais());
        formacao.put("ZAG", override.zagueiros());
        formacao.put("MEI", override.meias());
        formacao.put("ATA", override.atacantes());
        formacao.put("TEC", config.getFormacaoTec());
        return formacao;
    }

    /**
     * Seleciona os titulares maximizando a soma de score. Sem teto efetivo de
     * orcamento ({@code Double.MAX_VALUE}) usa a selecao gulosa por score, que ja
     * e otima nesse caso e evita regressao de performance. Com teto finito, delega
     * ao {@link OtimizadorTitulares} (branch-and-bound); se a guarda de iteracoes
     * do otimizador estourar, recorre a selecao gulosa como fallback.
     */
    private Map<Posicao, List<Atleta>> montarTitulares(Map<String, Integer> formacao,
                                                       Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                                       Configuracao config,
                                                       double budgetEfetivo) {
        if (budgetEfetivo != Double.MAX_VALUE) {
            OtimizadorTitulares.Resultado resultado = OtimizadorTitulares.otimizar(
                    formacao, candidatosPorPosicao, budgetEfetivo,
                    config.getLimiteAtletasPorClube(), config.isEvitarMesmoClubeDefesa());
            if (!resultado.fallback()) {
                return resultado.titulares();
            }
            log.warn("Otimizador atingiu a guarda de iteracoes; usando selecao gulosa por orcamento");
        }
        return montarGuloso(formacao, candidatosPorPosicao, config, budgetEfetivo);
    }

    /** Selecao gulosa por posicao, respeitando regras de clube e o budget efetivo. */
    private Map<Posicao, List<Atleta>> montarGuloso(Map<String, Integer> formacao,
                                                    Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                                    Configuracao config,
                                                    double budgetEfetivo) {
        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        Set<Integer> clubesDefesaEscalados = new HashSet<>();
        Map<Integer, Integer> contagemClubesTitulares = new HashMap<>();
        double[] budgetRestante = { budgetEfetivo };

        for (Map.Entry<String, Integer> slot : formacao.entrySet()) {
            Posicao posicao = Posicao.fromSigla(slot.getKey()).orElse(null);
            if (posicao == null) {
                log.warn("Posicao desconhecida na formacao: {}", slot.getKey());
                continue;
            }

            int qtd = slot.getValue();
            List<Atleta> candidatos = candidatosPorPosicao.getOrDefault(posicao, List.of());
            if (candidatos.isEmpty()) {
                log.warn("Sem jogadores para posicao {}", posicao);
                continue;
            }

            List<Atleta> escolhidos = escolherTitulares(
                    candidatos, qtd, posicao, config, clubesDefesaEscalados, contagemClubesTitulares,
                    budgetRestante, candidatosPorPosicao, formacao
            );
            titulares.put(posicao, escolhidos);
            log.debug("Titulares {}: {}", posicao,
                    escolhidos.stream().map(Atleta::getApelido).toList());
        }
        return titulares;
    }

    /**
     * Seleciona a reserva de cada posicao com reserva: somente Provaveis da mesma
     * posicao que nao sejam titulares, preferindo a mais barata que o titular mais
     * caro quando houver.
     */
    private Map<Posicao, Atleta> selecionarReservas(Map<Posicao, List<Atleta>> titulares,
                                                    Map<Posicao, List<Atleta>> candidatosPorPosicao) {
        Map<Posicao, Atleta> reservas = new EnumMap<>(Posicao.class);

        for (Map.Entry<Posicao, List<Atleta>> entry : titulares.entrySet()) {
            Posicao posicao = entry.getKey();
            if (!POSICOES_COM_RESERVA.contains(posicao)) {
                continue;
            }
            List<Atleta> escolhidos = entry.getValue();

            double maxPrecoTitular = escolhidos.stream()
                    .mapToDouble(Atleta::getPreco)
                    .max()
                    .orElse(0.0);

            Set<String> apelidosTitulares = escolhidos.stream()
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());

            List<Atleta> restantes = candidatosPorPosicao.getOrDefault(posicao, List.of()).stream()
                    .filter(a -> !apelidosTitulares.contains(a.getApelido()))
                    .filter(a -> a.getStatus() == StatusAtleta.PROVAVEL)
                    .toList();

            restantes.stream()
                    .filter(a -> a.getPreco() < maxPrecoTitular)
                    .findFirst()
                    .or(() -> restantes.stream().findFirst())
                    .ifPresent(r -> reservas.put(posicao, r));
        }
        return reservas;
    }

    private List<Atleta> escolherTitulares(List<Atleta> candidatos,
                                           int quantidade,
                                           Posicao posicao,
                                           Configuracao config,
                                           Set<Integer> clubesDefesaEscalados,
                                           Map<Integer, Integer> contagemClubesTitulares,
                                           double[] budgetRestante,
                                           Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                           Map<String, Integer> formacao) {
        List<Atleta> escolhidos = new ArrayList<>();
        for (Atleta candidato : candidatos) {
            if (escolhidos.size() == quantidade) {
                break;
            }
            if (podeEscalar(candidato, posicao, config, clubesDefesaEscalados, contagemClubesTitulares,
                    budgetRestante, escolhidos, quantidade, candidatosPorPosicao, formacao)) {
                escolhidos.add(candidato);
                registrarEscalacao(candidato, posicao, config.isEvitarMesmoClubeDefesa(), clubesDefesaEscalados, contagemClubesTitulares, budgetRestante);
            }
        }

        if (escolhidos.size() < quantidade) {
            completarMantendoLimitePorClube(
                    candidatos, quantidade, posicao, escolhidos, config, contagemClubesTitulares,
                    clubesDefesaEscalados, budgetRestante, candidatosPorPosicao, formacao
            );
        }

        if (escolhidos.size() < quantidade) {
            log.warn("Nao foi possivel respeitar todas as regras para {} ({}/{}), completando com melhor disponivel",
                    posicao, escolhidos.size(), quantidade);
            Set<String> apelidosEscolhidos = escolhidos.stream()
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());
            for (Atleta candidato : candidatos) {
                if (escolhidos.size() == quantidade) break;
                if (!apelidosEscolhidos.add(candidato.getApelido())) continue;
                if (!cabeNoBudgetComReserva(candidato, posicao, escolhidos, quantidade, budgetRestante, candidatosPorPosicao, formacao)) {
                    continue;
                }
                escolhidos.add(candidato);
                registrarEscalacao(candidato, posicao, config.isEvitarMesmoClubeDefesa(), clubesDefesaEscalados, contagemClubesTitulares, budgetRestante);
            }
        }

        return escolhidos;
    }

    private void completarMantendoLimitePorClube(List<Atleta> candidatos,
                                                  int quantidade,
                                                  Posicao posicao,
                                                  List<Atleta> escolhidos,
                                                  Configuracao config,
                                                  Map<Integer, Integer> contagemClubesTitulares,
                                                  Set<Integer> clubesDefesaEscalados,
                                                  double[] budgetRestante,
                                                  Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                                  Map<String, Integer> formacao) {
        if (escolhidos.size() >= quantidade) {
            return;
        }

        log.warn("Nao foi possivel preencher {} ({}/{}), tentando completar mantendo limite maximo por clube",
                posicao, escolhidos.size(), quantidade);

        Set<String> apelidosEscolhidos = escolhidos.stream()
                .map(Atleta::getApelido)
                .collect(Collectors.toSet());

        for (Atleta candidato : candidatos) {
            if (escolhidos.size() == quantidade) {
                break;
            }
            if (!apelidosEscolhidos.add(candidato.getApelido())) {
                continue;
            }
            if (atingiuLimitePorClube(candidato.getClubeId(), config.getLimiteAtletasPorClube(), contagemClubesTitulares)) {
                continue;
            }
            if (!cabeNoBudgetComReserva(candidato, posicao, escolhidos, quantidade, budgetRestante, candidatosPorPosicao, formacao)) {
                continue;
            }
            escolhidos.add(candidato);
            registrarEscalacao(candidato, posicao, false, clubesDefesaEscalados, contagemClubesTitulares, budgetRestante);
        }
    }

    private boolean podeEscalar(Atleta candidato,
                                Posicao posicao,
                                Configuracao config,
                                Set<Integer> clubesDefesaEscalados,
                                Map<Integer, Integer> contagemClubesTitulares,
                                double[] budgetRestante,
                                List<Atleta> escolhidos,
                                int quantidade,
                                Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                Map<String, Integer> formacao) {
        if (atingiuLimitePorClube(candidato.getClubeId(), config.getLimiteAtletasPorClube(), contagemClubesTitulares)) {
            return false;
        }

        if (!cabeNoBudgetComReserva(candidato, posicao, escolhidos, quantidade, budgetRestante, candidatosPorPosicao, formacao)) {
            return false;
        }

        return !config.isEvitarMesmoClubeDefesa()
                || !POSICOES_DEFESA.contains(posicao)
                || !clubesDefesaEscalados.contains(candidato.getClubeId());
    }

    private boolean atingiuLimitePorClube(int clubeId,
                                          int limite,
                                          Map<Integer, Integer> contagemClubesTitulares) {
        return contagemClubesTitulares.getOrDefault(clubeId, 0) >= limite;
    }

    private boolean cabeNoBudgetComReserva(Atleta candidato,
                                           Posicao posicao,
                                           List<Atleta> escolhidos,
                                           int quantidade,
                                           double[] budgetRestante,
                                           Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                           Map<String, Integer> formacao) {
        if (candidato.getPreco() > budgetRestante[0]) {
            return false;
        }

        double custoMinimoRestante = calcularCustoMinimoRestante(
                candidato, posicao, escolhidos, quantidade, candidatosPorPosicao, formacao);
        return candidato.getPreco() + custoMinimoRestante <= budgetRestante[0] + 0.000001;
    }

    private double calcularCustoMinimoRestante(Atleta candidato,
                                               Posicao posicaoAtual,
                                               List<Atleta> escolhidos,
                                               int quantidadeAtual,
                                               Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                               Map<String, Integer> formacao) {
        double custoMinimo = 0.0;
        boolean posicaoAtualEncontrada = false;

        for (Map.Entry<String, Integer> slot : formacao.entrySet()) {
            Posicao posicao = Posicao.fromSigla(slot.getKey()).orElse(null);
            if (posicao == null) {
                continue;
            }

            int vagasRestantes;
            if (posicao == posicaoAtual) {
                posicaoAtualEncontrada = true;
                vagasRestantes = Math.max(0, quantidadeAtual - escolhidos.size() - 1);
            } else if (posicaoAtualEncontrada) {
                vagasRestantes = slot.getValue();
            } else {
                continue;
            }

            if (vagasRestantes == 0) {
                continue;
            }

            Set<String> apelidosIgnorados = new HashSet<>();
            if (posicao == posicaoAtual) {
                escolhidos.stream().map(Atleta::getApelido).forEach(apelidosIgnorados::add);
                apelidosIgnorados.add(candidato.getApelido());
            }

            custoMinimo += candidatosPorPosicao.getOrDefault(posicao, List.of()).stream()
                    .filter(a -> !apelidosIgnorados.contains(a.getApelido()))
                    .mapToDouble(Atleta::getPreco)
                    .sorted()
                    .limit(vagasRestantes)
                    .sum();
        }

        return custoMinimo;
    }

    private void registrarEscalacao(Atleta atleta,
                                    Posicao posicao,
                                    boolean atualizarDefesa,
                                    Set<Integer> clubesDefesaEscalados,
                                    Map<Integer, Integer> contagemClubesTitulares,
                                    double[] budgetRestante) {
        contagemClubesTitulares.merge(atleta.getClubeId(), 1, Integer::sum);
        budgetRestante[0] -= atleta.getPreco();
        if (atualizarDefesa && POSICOES_DEFESA.contains(posicao)) {
            clubesDefesaEscalados.add(atleta.getClubeId());
        }
    }
}
