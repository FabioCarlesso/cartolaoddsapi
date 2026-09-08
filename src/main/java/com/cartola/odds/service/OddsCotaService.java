package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsCotaHistorico;
import com.cartola.odds.model.response.OddsCotaHistoricoResponse;
import com.cartola.odds.model.response.OddsCotaResponse;
import com.cartola.odds.repository.OddsCotaHistoricoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OddsCotaService {

    /**
     * Teto da janela consultavel: um trimestre, o suficiente para ver o mes corrente e os dois
     * anteriores lado a lado. Ver {@link #buscarHistorico(int)}.
     */
    static final int DIAS_MAXIMO = 92;

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
     * <p>O teto de {@link #DIAS_MAXIMO} e sobre o tamanho da <em>resposta</em>, nao sobre o da
     * tabela. A serie nao e agregada — cada leitura vira um item —, e a medicao com um ano de
     * dados deu 6.000 itens e 603 KB, meio megabyte para alimentar um grafico de algumas
     * centenas de pixels. Um trimestre fica em torno de 1.500 itens, e a janela padrao de 30
     * dias em ~500 itens e 50 KB. Se um dia fizer sentido olhar um ano inteiro, o caminho e
     * agregar por dia, e nao devolver tudo.
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

        // Truncado a microssegundos porque e a precisao que o PostgreSQL guarda em `timestamp`:
        // sem isso, `desde` sai com nanossegundos (do relogio da JVM) e os `instante` com
        // microssegundos, duas precisoes diferentes no mesmo payload.
        var desde    = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS).minusDays(dias);
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
     * <p>Dois sinais, porque a renovacao mexe nos dois numeros ao mesmo tempo: o consumo cai e o
     * saldo sobe. Olhar so o consumo deixa de fora o caso em que o ciclo anterior terminou com
     * um consumo baixissimo — raro, mas o saldo subindo e um sinal que ja esta na mao, e fora
     * de uma renovacao ele nao sobe.
     *
     * <p>A primeira leitura da janela nunca e marcada — sem uma anterior para comparar, dizer
     * que houve renovacao seria chute. Leituras sem um dos headers nao quebram a comparacao:
     * a referencia de cada numero segue sendo o ultimo valor conhecido dele.
     */
    private static List<OddsCotaHistoricoResponse.LeituraCotaDto> mapear(List<OddsCotaHistorico> leituras) {
        var dtos = new ArrayList<OddsCotaHistoricoResponse.LeituraCotaDto>(leituras.size());
        Long consumoAnterior = null;
        Long saldoAnterior   = null;

        for (var leitura : leituras) {
            Long consumo = leitura.getConsumoMes();
            Long saldo   = leitura.getSaldoRestante();

            boolean consumoCaiu = consumo != null && consumoAnterior != null && consumo < consumoAnterior;
            boolean saldoSubiu  = saldo   != null && saldoAnterior   != null && saldo   > saldoAnterior;

            dtos.add(OddsCotaHistoricoResponse.LeituraCotaDto.builder()
                    .instante(leitura.getInstante())
                    .saldoRestante(saldo)
                    .consumoMes(consumo)
                    .reinicioDeCota(consumoCaiu || saldoSubiu)
                    .build());

            if (consumo != null) consumoAnterior = consumo;
            if (saldo   != null) saldoAnterior   = saldo;
        }
        return dtos;
    }
}
