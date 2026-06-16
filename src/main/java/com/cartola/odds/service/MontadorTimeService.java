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
     * formacao persistida na configuracao; caso contrario, mantem as posicoes
     * fixas (GOL/LAT/TEC) da config e aplica ZAG/MEI/ATA do override.
     *
     * @param formacaoOverride formacao a aplicar nesta execucao (nao altera a config persistida)
     */
    public Time montar(List<Atleta> pool, int rodada, String avisoMercado, Double orcamento,
                       FormacaoConfig formacaoOverride) {
        Configuracao config = configuracaoService.buscarConfig();
        Map<String, Integer> formacao = resolverFormacao(config, formacaoOverride);

        // Quando um orcamento e informado, prioriza custo-beneficio (score/preco);
        // caso contrario mantem a ordenacao por score puro.
        Estrategia estrategia = orcamento != null ? Estrategia.CUSTO_BENEFICIO : Estrategia.SCORE_MAXIMO;
        Comparator<Atleta> ordenacao = estrategia == Estrategia.CUSTO_BENEFICIO
                ? Comparator.comparingDouble(MontadorTimeService::custoBeneficio).reversed()
                : Comparator.comparingDouble(Atleta::getScore).reversed();

        Map<Posicao, List<Atleta>> porPosicao = pool.stream()
                .collect(Collectors.groupingBy(Atleta::getPosicao));
        Map<Posicao, List<Atleta>> candidatosPorPosicao = new EnumMap<>(Posicao.class);
        porPosicao.forEach((posicao, atletas) -> candidatosPorPosicao.put(
                posicao,
                atletas.stream()
                        .sorted(ordenacao)
                        .toList()
        ));

        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        Map<Posicao, Atleta>       reservas  = new EnumMap<>(Posicao.class);
        Set<Integer> clubesDefesaEscalados = new HashSet<>();
        Map<Integer, Integer> contagemClubesTitulares = new HashMap<>();

        // Budget efetivo: orcamento da requisicao tem prioridade; senao usa budgetMaximo
        // da configuracao. Double.MAX_VALUE quando nao ha restricao (budgetMaximo == 0).
        double budgetEfetivo = orcamento != null
                ? orcamento
                : (config.getBudgetMaximo() > 0 ? config.getBudgetMaximo() : Double.MAX_VALUE);
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

            if (!POSICOES_COM_RESERVA.contains(posicao)) {
                continue;
            }

            double maxPrecoTitular = escolhidos.stream()
                    .mapToDouble(Atleta::getPreco)
                    .max()
                    .orElse(0.0);

            Set<String> apelidosTitulares = escolhidos.stream()
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());

            List<Atleta> restantes = candidatos.stream()
                    .filter(a -> !apelidosTitulares.contains(a.getApelido()))
                    .filter(a -> a.getStatus() == StatusAtleta.PROVAVEL)
                    .toList();

            restantes.stream()
                    .filter(a -> a.getPreco() < maxPrecoTitular)
                    .findFirst()
                    .or(() -> restantes.stream().findFirst())
                    .ifPresent(r -> reservas.put(posicao, r));
        }

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

    /** Pontos esperados por cartoleta gasta; usa o score quando o preco for invalido (<= 0). */
    private static double custoBeneficio(Atleta atleta) {
        return atleta.getPreco() > 0 ? atleta.getScore() / atleta.getPreco() : atleta.getScore();
    }

    /**
     * Resolve a formacao usada na montagem. Sem override, usa a formacao da
     * configuracao. Com override, mantem as posicoes fixas da config
     * (GOL/LAT/TEC) e aplica ZAG/MEI/ATA do override, preservando a ordem
     * (GOL, LAT, ZAG, MEI, ATA, TEC) exigida pelo calculo de custo minimo.
     */
    private Map<String, Integer> resolverFormacao(Configuracao config, FormacaoConfig override) {
        if (override == null) {
            return config.getFormacaoAsMap();
        }
        Map<String, Integer> formacao = new LinkedHashMap<>();
        formacao.put("GOL", config.getFormacaoGol());
        formacao.put("LAT", config.getFormacaoLat());
        formacao.put("ZAG", override.zagueiros());
        formacao.put("MEI", override.meias());
        formacao.put("ATA", override.atacantes());
        formacao.put("TEC", config.getFormacaoTec());
        return formacao;
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
