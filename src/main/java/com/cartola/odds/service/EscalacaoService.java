package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.EscalacaoRodada;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.response.EscalacaoRodadaResponse;
import com.cartola.odds.model.response.HistoricoResponse;
import com.cartola.odds.model.response.PontuadosResponse;
import com.cartola.odds.repository.EscalacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persiste o time sugerido em cada rodada e calcula a pontuacao real obtida
 * apos o fechamento da rodada, permitindo analise retroativa das sugestoes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalacaoService {

    private final EscalacaoRepository escalacaoRepository;
    private final CartolaClient cartolaClient;

    /**
     * Persiste a escalacao sugerida da rodada (titulares + reservas).
     * Idempotente: se ja existir escalacao para a rodada, nao sobrescreve.
     *
     * @param time     time montado pelo pipeline
     * @param rodadaId rodada a registrar
     */
    @Transactional
    public void salvarEscalacao(Time time, Integer rodadaId) {
        if (escalacaoRepository.existsByRodadaId(rodadaId)) {
            log.info("Escalacao da rodada {} ja registrada — ignorando (idempotente)", rodadaId);
            return;
        }

        Integer capitaoId    = time.getCapitao()     != null ? time.getCapitao().getAtletaId()     : null;
        Integer reservaLuxoId = time.getReservaLuxo() != null ? time.getReservaLuxo().getAtletaId() : null;

        List<EscalacaoRodada> registros = new ArrayList<>();
        Set<Integer> jaRegistrados = new HashSet<>();

        time.getTitulares().values().stream()
                .flatMap(List::stream)
                .forEach(atleta -> adicionar(registros, jaRegistrados, atleta, rodadaId, capitaoId, reservaLuxoId));

        time.getReservas().values()
                .forEach(atleta -> adicionar(registros, jaRegistrados, atleta, rodadaId, capitaoId, reservaLuxoId));

        escalacaoRepository.saveAll(registros);
        log.info("Escalacao da rodada {} registrada com {} atletas", rodadaId, registros.size());
    }

    private void adicionar(List<EscalacaoRodada> registros, Set<Integer> jaRegistrados, Atleta atleta,
                           Integer rodadaId, Integer capitaoId, Integer reservaLuxoId) {
        if (atleta == null || !jaRegistrados.add(atleta.getAtletaId())) {
            return;
        }

        var e = new EscalacaoRodada();
        e.setRodadaId(rodadaId);
        e.setAtletaId(atleta.getAtletaId());
        e.setApelido(atleta.getApelido());
        e.setPosicao(atleta.getPosicao().name());
        e.setClube(atleta.getNomeClube());
        e.setScoreSugerido(atleta.getScore());
        e.setPreco(atleta.getPreco());
        e.setCapitao(capitaoId != null && capitaoId.equals(atleta.getAtletaId()));
        e.setReservaLuxo(reservaLuxoId != null && reservaLuxoId.equals(atleta.getAtletaId()));
        e.setEmDuvida(atleta.isDuvida());
        e.setCriadoEm(LocalDateTime.now());
        registros.add(e);
    }

    /**
     * Consulta /atletas/pontuados da rodada e preenche a pontuacao real dos atletas registrados.
     *
     * <p>O endpoint /atletas/pontuados do Cartola expoe somente a rodada corrente; por isso so
     * permitimos atualizar quando {@code rodadaId} for a rodada atual, evitando gravar a pontuacao
     * de uma rodada na outra. Chame logo apos o fechamento da rodada.
     *
     * <p>A leitura e a chamada HTTP externa acontecem fora de qualquer transacao de escrita; a
     * persistencia final fica a cargo do {@code saveAll}, que abre a propria transacao.
     *
     * @param rodadaId rodada a atualizar
     * @return escalacao detalhada com as pontuacoes preenchidas
     * @throws RecursoNaoEncontradoException se nao houver escalacao registrada para a rodada
     * @throws IllegalArgumentException      se a rodada solicitada nao for a rodada corrente
     */
    public EscalacaoRodadaResponse atualizarPontuacaoReal(Integer rodadaId) {
        List<EscalacaoRodada> escalacoes = escalacaoRepository.findByRodadaIdOrderById(rodadaId);
        if (escalacoes.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Nenhuma escalacao registrada para a rodada " + rodadaId);
        }

        int rodadaAtual = cartolaClient.buscarStatusMercado().getRodadaAtual();
        if (!rodadaId.equals(rodadaAtual)) {
            throw new IllegalArgumentException(
                    "So e possivel atualizar a pontuacao da rodada corrente (" + rodadaAtual
                            + "). Rodada solicitada: " + rodadaId
                            + ". Chame este endpoint logo apos o fechamento da rodada.");
        }

        Map<Integer, Double> pontuacoes = buscarPontuacoes(rodadaId);
        escalacoes.forEach(e -> {
            Double pontuacao = pontuacoes.get(e.getAtletaId());
            if (pontuacao != null) {
                e.setPontuacaoReal(pontuacao);
            }
        });

        escalacaoRepository.saveAll(escalacoes);
        log.info("Pontuacao real da rodada {} atualizada ({} atletas com pontuacao)",
                rodadaId, pontuacoes.size());

        return EscalacaoRodadaResponse.from(rodadaId, escalacoes);
    }

    /** Lista todas as rodadas registradas com resumo de score sugerido vs. real. */
    @Transactional(readOnly = true)
    public HistoricoResponse buscarHistorico() {
        Map<Integer, List<EscalacaoRodada>> porRodada = new LinkedHashMap<>();
        escalacaoRepository.findAllByOrderByRodadaIdAscIdAsc()
                .forEach(e -> porRodada.computeIfAbsent(e.getRodadaId(), k -> new ArrayList<>()).add(e));

        List<HistoricoResponse.RodadaResumo> rodadas = porRodada.entrySet().stream()
                .map(entry -> montarResumo(entry.getKey(), entry.getValue()))
                .toList();

        return HistoricoResponse.builder()
                .totalRodadas(rodadas.size())
                .rodadas(rodadas)
                .build();
    }

    /** Retorna a escalacao detalhada de uma rodada especifica. */
    @Transactional(readOnly = true)
    public EscalacaoRodadaResponse buscarPorRodada(Integer rodadaId) {
        List<EscalacaoRodada> escalacoes = escalacaoRepository.findByRodadaIdOrderById(rodadaId);
        if (escalacoes.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Nenhuma escalacao registrada para a rodada " + rodadaId);
        }
        return EscalacaoRodadaResponse.from(rodadaId, escalacoes);
    }

    private HistoricoResponse.RodadaResumo montarResumo(Integer rodadaId, List<EscalacaoRodada> escalacoes) {
        double scoreSugeridoTotal = escalacoes.stream()
                .mapToDouble(EscalacaoRodada::getScoreSugerido)
                .sum();

        boolean disponivel = escalacoes.stream().anyMatch(e -> e.getPontuacaoReal() != null);

        Double pontuacaoRealTotal = null;
        if (disponivel) {
            // Capitao pontua em dobro no Cartola
            double total = escalacoes.stream()
                    .filter(e -> e.getPontuacaoReal() != null)
                    .mapToDouble(e -> e.isCapitao() ? e.getPontuacaoReal() * 2 : e.getPontuacaoReal())
                    .sum();
            pontuacaoRealTotal = arredondar(total);
        }

        return HistoricoResponse.RodadaResumo.builder()
                .rodadaId(rodadaId)
                .criadoEm(escalacoes.get(0).getCriadoEm())
                .totalAtletas(escalacoes.size())
                .scoreSugeridoTotal(arredondar(scoreSugeridoTotal))
                .pontuacaoRealTotal(pontuacaoRealTotal)
                .pontuacaoRealDisponivel(disponivel)
                .build();
    }

    /** Extrai um mapa atletaId -> pontuacao da rodada a partir do /atletas/pontuados. */
    private Map<Integer, Double> buscarPontuacoes(Integer rodadaId) {
        PontuadosResponse pontuados = cartolaClient.buscarPontuados(rodadaId);
        if (pontuados == null || pontuados.getAtletas() == null) {
            log.warn("Sem dados de pontuados para a rodada {}", rodadaId);
            return Map.of();
        }

        Map<Integer, Double> pontuacoes = new HashMap<>();
        pontuados.getAtletas().forEach((idStr, pontuado) -> {
            if (pontuado.getPontuacaoNum() == null) return;
            try {
                pontuacoes.put(Integer.parseInt(idStr), pontuado.getPontuacaoNum());
            } catch (NumberFormatException ex) {
                log.debug("ID de atleta invalido em pontuados: {}", idStr);
            }
        });
        return pontuacoes;
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
