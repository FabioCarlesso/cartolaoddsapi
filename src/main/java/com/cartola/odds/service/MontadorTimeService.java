package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
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

    private final ConfiguracaoService configuracaoService;

    public Time montar(List<Atleta> pool, int rodada, String avisoMercado) {
        Configuracao config = configuracaoService.buscarConfig();

        Map<Posicao, List<Atleta>> porPosicao = pool.stream()
                .collect(Collectors.groupingBy(Atleta::getPosicao));

        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        Map<Posicao, Atleta>       reservas  = new EnumMap<>(Posicao.class);
        Set<Integer> clubesDefesaEscalados = new HashSet<>();

        for (Map.Entry<String, Integer> slot : config.getFormacaoAsMap().entrySet()) {
            Posicao posicao = Posicao.fromSigla(slot.getKey()).orElse(null);
            if (posicao == null) {
                log.warn("Posicao desconhecida na formacao: {}", slot.getKey());
                continue;
            }

            int qtd = slot.getValue();
            List<Atleta> candidatos = porPosicao.getOrDefault(posicao, List.of()).stream()
                    .sorted(Comparator.comparingDouble(Atleta::getScore).reversed())
                    .toList();

            if (candidatos.isEmpty()) {
                log.warn("Sem jogadores para posicao {}", posicao);
                continue;
            }

            List<Atleta> escolhidos = escolherTitulares(candidatos, qtd, posicao, config, clubesDefesaEscalados);
            titulares.put(posicao, escolhidos);
            log.debug("Titulares {}: {}", posicao,
                    escolhidos.stream().map(Atleta::getApelido).toList());

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

        Atleta reservaLuxo = todosTitulares.stream()
                .filter(a -> capitao == null || !a.getApelido().equals(capitao.getApelido()))
                .max(Comparator.comparingDouble(Atleta::getScore))
                .orElse(null);

        if (capitao    != null) log.info("Capitao: {}",      capitao.formatado());
        if (reservaLuxo != null) log.info("Reserva de Luxo: {}", reservaLuxo.formatado());

        double custoTotal = titularesEnriquecidos.values().stream()
                .flatMap(List::stream)
                .mapToDouble(Atleta::getPreco)
                .sum();

        log.info("Custo total dos titulares: C${}", String.format("%.1f", custoTotal));

        return Time.builder()
                .rodada(rodada)
                .avisoMercado(avisoMercado)
                .titulares(titularesEnriquecidos)
                .reservas(reservas)
                .capitao(capitao)
                .reservaLuxo(reservaLuxo)
                .alertasDuvida(alertas)
                .custoTotal(custoTotal)
                .build();
    }

    private List<Atleta> escolherTitulares(List<Atleta> candidatos,
                                           int quantidade,
                                           Posicao posicao,
                                           Configuracao config,
                                           Set<Integer> clubesDefesaEscalados) {
        if (!config.isEvitarMesmoClubeDefesa() || !POSICOES_DEFESA.contains(posicao)) {
            return new ArrayList<>(candidatos.subList(0, Math.min(quantidade, candidatos.size())));
        }

        List<Atleta> escolhidos = new ArrayList<>();
        for (Atleta candidato : candidatos) {
            if (escolhidos.size() == quantidade) {
                break;
            }
            if (clubesDefesaEscalados.add(candidato.getClubeId())) {
                escolhidos.add(candidato);
            }
        }

        if (escolhidos.size() < quantidade) {
            log.warn("Regra de defesa sem clube repetido limitou {} para {}/{} titulares",
                    posicao, escolhidos.size(), quantidade);
        }

        return escolhidos;
    }
}
