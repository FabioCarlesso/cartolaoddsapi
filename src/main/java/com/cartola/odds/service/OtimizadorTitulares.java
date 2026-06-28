package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.enums.Posicao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Otimizador de titulares sob orcamento.
 *
 * <p>Resolve um <strong>multiple-choice knapsack por posicao</strong> via
 * <strong>branch-and-bound</strong>, maximizando a soma de score com a soma de
 * preco menor ou igual ao orcamento, respeitando a formacao, o limite de atletas
 * por clube e a regra de nao repetir clube na defesa (GOL/LAT/ZAG). Em empate de
 * score (dentro de um epsilon), prefere a solucao de <strong>menor custo</strong>
 * (equivalente a maior {@code score/preco}).
 *
 * <p>Quando o orcamento nao permite completar a formacao, retorna o melhor time
 * best-effort dentro do teto (maximizando primeiro o numero de vagas preenchidas
 * e depois o score) com {@link Resultado#completo()} {@code false}.
 *
 * <p>Para conter o custo do B&amp;B em pools grandes, aplica poda de viabilidade
 * de orcamento, poda por limite superior de score (admissivel), filtro de
 * fronteira de Pareto por clube e uma guarda de iteracoes. Ao estourar a guarda,
 * sinaliza {@link Resultado#fallback()} para que o chamador use a selecao gulosa.
 */
final class OtimizadorTitulares {

    /** Teto de iteracoes do B&B; ao ultrapassar, sinaliza fallback para o montador. */
    private static final long LIMITE_ITERACOES = 2_000_000L;
    private static final double EPS = 1e-9;

    private static final Set<Posicao> POSICOES_DEFESA = Set.of(Posicao.GOL, Posicao.LAT, Posicao.ZAG);

    /**
     * Resultado da otimizacao.
     *
     * @param titulares selecao por posicao (vazia para posicoes nao preenchidas)
     * @param completo  {@code true} quando todas as vagas da formacao foram preenchidas
     * @param fallback  {@code true} quando a guarda de iteracoes estourou e o
     *                  chamador deve recorrer a selecao gulosa
     */
    record Resultado(Map<Posicao, List<Atleta>> titulares, boolean completo, boolean fallback) {}

    private final List<Posicao> posicoes = new ArrayList<>();
    private final List<Integer> quantidades = new ArrayList<>();
    private final List<List<Atleta>> candidatos = new ArrayList<>();

    // Estruturas de poda pre-computadas por posicao.
    private final List<double[]> topScorePrefixo = new ArrayList<>();   // top-k scores (desc)
    private final List<double[]> cheapPrecoPrefixo = new ArrayList<>(); // k precos mais baratos
    private double[] topScoreSufixo;   // soma do top-qtd das posicoes >= i (vagas cheias)
    private double[] cheapPrecoSufixo;  // soma do qtd mais baratos das posicoes >= i

    private final double orcamento;
    private final int limitePorClube;
    private final boolean evitarMesmoClubeDefesa;
    private final int totalVagas;

    // Estado mutavel da busca.
    private final List<List<Atleta>> selecao = new ArrayList<>();
    private final Map<Integer, Integer> contagemClubes = new HashMap<>();
    private final Set<Integer> clubesDefesa = new HashSet<>();
    private long iteracoes = 0;
    private boolean fallback = false;

    private Map<Posicao, List<Atleta>> melhorCompleto;
    private double melhorScoreCompleto = Double.NEGATIVE_INFINITY;
    private double melhorCustoCompleto = Double.POSITIVE_INFINITY;
    private boolean temCompleto = false;

    private Map<Posicao, List<Atleta>> melhorParcial;
    private int melhorVagasParcial = -1;
    private double melhorScoreParcial = Double.NEGATIVE_INFINITY;
    private double melhorCustoParcial = Double.POSITIVE_INFINITY;

    private OtimizadorTitulares(Map<String, Integer> formacao,
                                Map<Posicao, List<Atleta>> candidatosPorPosicao,
                                double orcamento,
                                int limitePorClube,
                                boolean evitarMesmoClubeDefesa) {
        this.orcamento = orcamento;
        this.limitePorClube = limitePorClube;
        this.evitarMesmoClubeDefesa = evitarMesmoClubeDefesa;

        int vagas = 0;
        for (Map.Entry<String, Integer> slot : formacao.entrySet()) {
            Posicao posicao = Posicao.fromSigla(slot.getKey()).orElse(null);
            if (posicao == null || slot.getValue() == null || slot.getValue() <= 0) {
                continue;
            }
            List<Atleta> elegiveis = candidatosPorPosicao.getOrDefault(posicao, List.of()).stream()
                    .filter(a -> a.getPreco() <= orcamento + EPS)
                    .collect(Collectors.toList());
            elegiveis = filtrarParetoPorClube(elegiveis);
            elegiveis.sort(Comparator.comparingDouble(Atleta::getScore).reversed()
                    .thenComparingDouble(Atleta::getPreco));

            posicoes.add(posicao);
            quantidades.add(slot.getValue());
            candidatos.add(elegiveis);
            selecao.add(new ArrayList<>());
            vagas += slot.getValue();
        }
        this.totalVagas = vagas;
        precomputarPodas();
    }

    static Resultado otimizar(Map<String, Integer> formacao,
                              Map<Posicao, List<Atleta>> candidatosPorPosicao,
                              double orcamento,
                              int limitePorClube,
                              boolean evitarMesmoClubeDefesa) {
        OtimizadorTitulares otimizador = new OtimizadorTitulares(
                formacao, candidatosPorPosicao, orcamento, limitePorClube, evitarMesmoClubeDefesa);
        return otimizador.resolver();
    }

    private Resultado resolver() {
        buscar(0, 0, 0, orcamento, 0.0, 0.0, 0);

        if (fallback) {
            return new Resultado(Map.of(), false, true);
        }
        if (temCompleto) {
            return new Resultado(melhorCompleto, true, false);
        }
        return new Resultado(melhorParcial != null ? melhorParcial : Map.of(), false, false);
    }

    private void buscar(int posIndex, int pickedInPos, int startIdx, double budget,
                        double scoreAcc, double custoAcc, int vagasPreenchidas) {
        if (fallback) {
            return;
        }
        if (++iteracoes > LIMITE_ITERACOES) {
            fallback = true;
            return;
        }

        if (!temCompleto) {
            registrarParcial(vagasPreenchidas, scoreAcc, custoAcc);
        }

        if (posIndex == posicoes.size()) {
            if (vagasPreenchidas == totalVagas) {
                registrarCompleto(scoreAcc, custoAcc);
            }
            return;
        }

        int qtd = quantidades.get(posIndex);
        if (pickedInPos == qtd) {
            buscar(posIndex + 1, 0, 0, budget, scoreAcc, custoAcc, vagasPreenchidas);
            return;
        }

        // Poda de viabilidade de orcamento: custo minimo para completar a formacao.
        boolean podeCompletar = custoMinimoRestante(posIndex, pickedInPos) <= budget + EPS;
        if (!podeCompletar && temCompleto) {
            return; // solucao parcial nunca supera uma completa
        }
        // Poda por limite superior de score (admissivel).
        if (temCompleto) {
            double limiteSuperior = scoreAcc + scoreMaximoRestante(posIndex, pickedInPos);
            if (limiteSuperior < melhorScoreCompleto - EPS) {
                return;
            }
        }

        List<Atleta> cands = candidatos.get(posIndex);
        for (int i = startIdx; i < cands.size(); i++) {
            Atleta atleta = cands.get(i);
            if (atleta.getPreco() > budget + EPS) {
                continue;
            }
            if (!podeEscalar(atleta, posIndex)) {
                continue;
            }
            aplicar(atleta, posIndex);
            buscar(posIndex, pickedInPos + 1, i + 1, budget - atleta.getPreco(),
                    scoreAcc + atleta.getScore(), custoAcc + atleta.getPreco(), vagasPreenchidas + 1);
            desfazer(atleta, posIndex);
            if (fallback) {
                return;
            }
        }

        // Vaga nao preenchida: so interessa para best-effort enquanto nao houver time completo.
        if (!temCompleto) {
            buscar(posIndex + 1, 0, 0, budget, scoreAcc, custoAcc, vagasPreenchidas);
        }
    }

    private boolean podeEscalar(Atleta atleta, int posIndex) {
        if (contagemClubes.getOrDefault(atleta.getClubeId(), 0) >= limitePorClube) {
            return false;
        }
        return !evitarMesmoClubeDefesa
                || !POSICOES_DEFESA.contains(posicoes.get(posIndex))
                || !clubesDefesa.contains(atleta.getClubeId());
    }

    private void aplicar(Atleta atleta, int posIndex) {
        selecao.get(posIndex).add(atleta);
        contagemClubes.merge(atleta.getClubeId(), 1, Integer::sum);
        if (atualizaDefesa(posIndex)) {
            clubesDefesa.add(atleta.getClubeId());
        }
    }

    private void desfazer(Atleta atleta, int posIndex) {
        List<Atleta> lista = selecao.get(posIndex);
        lista.remove(lista.size() - 1);
        int restante = contagemClubes.merge(atleta.getClubeId(), -1, Integer::sum);
        if (restante <= 0) {
            contagemClubes.remove(atleta.getClubeId());
        }
        if (atualizaDefesa(posIndex)) {
            clubesDefesa.remove(atleta.getClubeId());
        }
    }

    private boolean atualizaDefesa(int posIndex) {
        return evitarMesmoClubeDefesa && POSICOES_DEFESA.contains(posicoes.get(posIndex));
    }

    private void registrarCompleto(double scoreAcc, double custoAcc) {
        boolean melhor = !temCompleto
                || scoreAcc > melhorScoreCompleto + EPS
                || (Math.abs(scoreAcc - melhorScoreCompleto) <= EPS && custoAcc < melhorCustoCompleto - EPS);
        if (melhor) {
            melhorCompleto = snapshot();
            melhorScoreCompleto = scoreAcc;
            melhorCustoCompleto = custoAcc;
            temCompleto = true;
        }
    }

    private void registrarParcial(int vagas, double scoreAcc, double custoAcc) {
        boolean melhor = vagas > melhorVagasParcial
                || (vagas == melhorVagasParcial && scoreAcc > melhorScoreParcial + EPS)
                || (vagas == melhorVagasParcial && Math.abs(scoreAcc - melhorScoreParcial) <= EPS
                        && custoAcc < melhorCustoParcial - EPS);
        if (melhor) {
            melhorParcial = snapshot();
            melhorVagasParcial = vagas;
            melhorScoreParcial = scoreAcc;
            melhorCustoParcial = custoAcc;
        }
    }

    private Map<Posicao, List<Atleta>> snapshot() {
        Map<Posicao, List<Atleta>> mapa = new EnumMap<>(Posicao.class);
        for (int i = 0; i < posicoes.size(); i++) {
            List<Atleta> escolhidos = selecao.get(i);
            if (!escolhidos.isEmpty()) {
                mapa.put(posicoes.get(i), new ArrayList<>(escolhidos));
            }
        }
        return mapa;
    }

    /** Limite superior admissivel de score adicional para as vagas restantes. */
    private double scoreMaximoRestante(int posIndex, int pickedInPos) {
        int rem = quantidades.get(posIndex) - pickedInPos;
        double[] prefixo = topScorePrefixo.get(posIndex);
        double atual = prefixo[Math.min(rem, prefixo.length - 1)];
        double sufixo = posIndex + 1 < topScoreSufixo.length ? topScoreSufixo[posIndex + 1] : 0.0;
        return atual + sufixo;
    }

    /** Custo minimo (lower bound) para completar todas as vagas restantes. */
    private double custoMinimoRestante(int posIndex, int pickedInPos) {
        int rem = quantidades.get(posIndex) - pickedInPos;
        double[] prefixo = cheapPrecoPrefixo.get(posIndex);
        int disponiveis = prefixo.length - 1;
        if (rem > disponiveis) {
            return Double.POSITIVE_INFINITY; // impossivel completar esta posicao
        }
        double atual = prefixo[rem];
        double sufixo = posIndex + 1 < cheapPrecoSufixo.length ? cheapPrecoSufixo[posIndex + 1] : 0.0;
        return atual + sufixo;
    }

    private void precomputarPodas() {
        int n = posicoes.size();
        topScoreSufixo = new double[n + 1];
        cheapPrecoSufixo = new double[n + 1];

        for (int i = 0; i < n; i++) {
            List<Atleta> cands = candidatos.get(i);
            int qtd = quantidades.get(i);

            double[] scores = cands.stream()
                    .mapToDouble(Atleta::getScore)
                    .boxed()
                    .sorted(Comparator.reverseOrder())
                    .limit(qtd)
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            double[] topPref = new double[scores.length + 1];
            for (int k = 0; k < scores.length; k++) {
                topPref[k + 1] = topPref[k] + scores[k];
            }
            topScorePrefixo.add(topPref);

            double[] precos = cands.stream()
                    .mapToDouble(Atleta::getPreco)
                    .sorted()
                    .toArray();
            double[] cheapPref = new double[precos.length + 1];
            for (int k = 0; k < precos.length; k++) {
                cheapPref[k + 1] = cheapPref[k] + precos[k];
            }
            cheapPrecoPrefixo.add(cheapPref);
        }

        for (int i = n - 1; i >= 0; i--) {
            int qtd = quantidades.get(i);
            double[] topPref = topScorePrefixo.get(i);
            double[] cheapPref = cheapPrecoPrefixo.get(i);
            double topQtd = topPref[Math.min(qtd, topPref.length - 1)];
            double cheapQtd = qtd <= cheapPref.length - 1
                    ? cheapPref[qtd]
                    : Double.POSITIVE_INFINITY;
            topScoreSufixo[i] = topQtd + topScoreSufixo[i + 1];
            cheapPrecoSufixo[i] = cheapQtd + cheapPrecoSufixo[i + 1];
        }
    }

    /**
     * Remove atletas dominados (score menor e preco maior ou igual) <em>dentro do
     * mesmo clube</em>. A filtragem por clube preserva a diversidade necessaria
     * para as restricoes de clube: um atleta so e descartado se outro do mesmo
     * clube o domina, mantendo intacta a fronteira de Pareto entre clubes distintos.
     */
    private static List<Atleta> filtrarParetoPorClube(List<Atleta> atletas) {
        Map<Integer, List<Atleta>> porClube = atletas.stream()
                .collect(Collectors.groupingBy(Atleta::getClubeId));
        List<Atleta> resultado = new ArrayList<>();
        for (List<Atleta> doClube : porClube.values()) {
            List<Atleta> ordenados = doClube.stream()
                    .sorted(Comparator.comparingDouble(Atleta::getScore).reversed()
                            .thenComparingDouble(Atleta::getPreco))
                    .toList();
            double menorPreco = Double.POSITIVE_INFINITY;
            for (Atleta atleta : ordenados) {
                if (atleta.getPreco() < menorPreco - EPS) {
                    resultado.add(atleta);
                    menorPreco = atleta.getPreco();
                }
            }
        }
        return resultado;
    }
}
