package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import com.cartola.odds.model.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servico responsavel por construir o relatorio de pagamento dos profissionais
 * (atletas) escalados na rodada.
 *
 * <p>O "pagamento" e o custo em cartoletas (C$) pago pelo gestor para escalar
 * cada profissional. O relatorio inclui titulares e reservas e identifica o
 * capitao.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioPagamentoService {

    public static final String MOEDA = "C$";

    private final PipelineService pipelineService;

    public RelatorioPagamento gerar() {
        log.info("Gerando relatorio de pagamento dos profissionais...");
        Time time = pipelineService.executar();
        return montarRelatorio(time);
    }

    public RelatorioPagamento montarRelatorio(Time time) {
        var capitaoId = time.getCapitao() != null ? time.getCapitao().getAtletaId() : -1;
        List<ItemPagamento> itens = new ArrayList<>();

        time.getTitulares().forEach((posicao, atletas) ->
                atletas.forEach(a -> itens.add(toItem(a, true, a.getAtletaId() == capitaoId))));

        time.getReservas().forEach((posicao, atleta) ->
                itens.add(toItem(atleta, false, false)));

        itens.sort(Comparator.comparingDouble(ItemPagamento::getValorPagamento).reversed()
                .thenComparing(ItemPagamento::getApelido, Comparator.nullsLast(Comparator.naturalOrder())));

        double total = itens.stream().mapToDouble(ItemPagamento::getValorPagamento).sum();

        return RelatorioPagamento.builder()
                .rodada(time.getRodada())
                .avisoMercado(time.getAvisoMercado())
                .geradoEm(LocalDateTime.now())
                .moeda(MOEDA)
                .totalProfissionais(itens.size())
                .totalPagamento(arredondar(total))
                .itens(List.copyOf(itens))
                .build();
    }

    private ItemPagamento toItem(Atleta a, boolean titular, boolean capitao) {
        return ItemPagamento.builder()
                .atletaId(a.getAtletaId())
                .apelido(a.getApelido())
                .posicao(a.getPosicao())
                .siglaClube(a.getSiglaClube())
                .nomeClube(a.getNomeClube())
                .status(a.getStatus())
                .valorPagamento(arredondar(a.getPreco()))
                .titular(titular)
                .capitao(capitao)
                .build();
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
