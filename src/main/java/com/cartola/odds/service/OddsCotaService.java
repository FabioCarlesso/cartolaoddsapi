package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsCotaHistorico;
import com.cartola.odds.model.response.OddsCotaHistoricoResponse;
import com.cartola.odds.model.response.OddsCotaResponse;
import com.cartola.odds.repository.OddsCotaHistoricoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OddsCotaService {

    /** Teto da janela consultavel. Ver {@link #buscarHistorico(int)}. */
    static final int DIAS_MAXIMO = 365;

    private final OddsClient                  oddsClient;
    private final OddsProperties              oddsProperties;
    private final OddsCotaHistoricoRepository historicoRepository;

    public OddsCotaResponse buscarCota() {
        return OddsCotaResponse.builder()
                .saldoRestante(oddsClient.getRequestsRemaining())
                .consumoMes(oddsClient.getRequestsUsed())
                .ultimaLeitura(oddsClient.getUltimaLeitura())
                .minRequestsRemaining(oddsProperties.getMinRequestsRemaining())
                .guardrailAtivo(oddsClient.isGuardrailAtivo())
                .ultimaSondagem(oddsClient.getUltimaSondagem())
                .proximaSondagem(oddsClient.getProximaSondagem())
                .build();
    }

    /**
     * Serie das leituras de cota dos ultimos {@code dias}, em ordem cronologica (#61).
     *
     * <p>O teto de {@link #DIAS_MAXIMO} nao existe por medo do volume — a tabela cresce no
     * maximo ~500 linhas por mes, porque uma linha so nasce de uma chamada ao provedor e as
     * chamadas sao limitadas pela propria cota. Ele existe para que um {@code ?dias=999999} nao
     * vire uma varredura da tabela inteira vinda da barra de endereco.
     *
     * @throws IllegalArgumentException quando a janela nao descreve um intervalo consultavel;
     *         o {@code GlobalExceptionHandler} traduz para 400 com a mensagem.
     */
    public OddsCotaHistoricoResponse buscarHistorico(int dias) {
        if (dias < 1) {
            throw new IllegalArgumentException(
                    "dias deve ser maior que 0. Valor informado: " + dias);
        }
        if (dias > DIAS_MAXIMO) {
            throw new IllegalArgumentException(
                    "dias deve ser no maximo " + DIAS_MAXIMO + ". Valor informado: " + dias);
        }

        var desde    = LocalDateTime.now().minusDays(dias);
        var leituras = historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAsc(desde);

        return OddsCotaHistoricoResponse.builder()
                .dias(dias)
                .desde(desde)
                .total(leituras.size())
                .leituras(mapear(leituras))
                .build();
    }

    /**
     * Marca onde o provedor renovou a cota. A deteccao acontece aqui, uma vez, em vez de em
     * cada consumidor: o grafico do mes precisa saber onde quebrar a linha, e um consumo que
     * cai lido sem esse contexto parece falha de coleta, nao virada de ciclo.
     *
     * <p>A primeira leitura da janela nunca e marcada — sem uma anterior para comparar, dizer
     * que houve renovacao seria chute. Leituras sem {@code consumoMes} (o header nao veio) nao
     * quebram a comparacao: a referencia segue sendo o ultimo consumo conhecido.
     */
    private static List<OddsCotaHistoricoResponse.LeituraCotaDto> mapear(List<OddsCotaHistorico> leituras) {
        var dtos = new ArrayList<OddsCotaHistoricoResponse.LeituraCotaDto>(leituras.size());
        Long consumoAnterior = null;

        for (var leitura : leituras) {
            Long consumo = leitura.getConsumoMes();
            boolean reinicio = consumo != null && consumoAnterior != null && consumo < consumoAnterior;

            dtos.add(OddsCotaHistoricoResponse.LeituraCotaDto.builder()
                    .instante(leitura.getInstante())
                    .saldoRestante(leitura.getSaldoRestante())
                    .consumoMes(consumo)
                    .reinicioDeCota(reinicio)
                    .build());

            if (consumo != null) consumoAnterior = consumo;
        }
        return dtos;
    }
}
