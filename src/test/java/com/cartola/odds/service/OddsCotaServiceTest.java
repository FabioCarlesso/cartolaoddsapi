package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.config.OddsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OddsCotaService")
class OddsCotaServiceTest {

    @Mock OddsClient oddsClient;

    @Test
    @DisplayName("deve montar a resposta com os dados atuais do OddsClient")
    void deveMontarRespostaComDadosAtuais() {
        var agora = LocalDateTime.now();
        when(oddsClient.getRequestsRemaining()).thenReturn(412L);
        when(oddsClient.getRequestsUsed()).thenReturn(88L);
        when(oddsClient.getUltimaLeitura()).thenReturn(agora);
        when(oddsClient.isGuardrailAtivo()).thenReturn(false);

        var oddsCotaService = new OddsCotaService(oddsClient, propriedadesComMinimo(50));
        var resposta = oddsCotaService.buscarCota();

        assertThat(resposta.getSaldoRestante()).isEqualTo(412L);
        assertThat(resposta.getConsumoMes()).isEqualTo(88L);
        assertThat(resposta.getUltimaLeitura()).isEqualTo(agora);
        assertThat(resposta.getMinRequestsRemaining()).isEqualTo(50);
        assertThat(resposta.isGuardrailAtivo()).isFalse();
    }

    @Test
    @DisplayName("deve dizer quando a proxima sondagem destrava o guardrail")
    void deveExporJanelaDeSondagem() {
        // Com guardrailAtivo=true, essa e a unica pergunta que sobra para quem esta olhando:
        // quando isso volta sozinho. Sem o campo, a resposta so existia no log.
        var sondagem = LocalDateTime.now().minusHours(2);
        var proxima  = sondagem.plusHours(24);
        when(oddsClient.getRequestsRemaining()).thenReturn(10L);
        when(oddsClient.getRequestsUsed()).thenReturn(490L);
        when(oddsClient.isGuardrailAtivo()).thenReturn(true);
        when(oddsClient.getUltimaSondagem()).thenReturn(sondagem);
        when(oddsClient.getProximaSondagem()).thenReturn(proxima);

        var resposta = new OddsCotaService(oddsClient, propriedadesComMinimo(50)).buscarCota();

        assertThat(resposta.isGuardrailAtivo()).isTrue();
        assertThat(resposta.getUltimaSondagem()).isEqualTo(sondagem);
        assertThat(resposta.getProximaSondagem()).isEqualTo(proxima);
    }

    @Test
    @DisplayName("deve refletir guardrail ativo e valores nulos sem leitura ainda")
    void deveRefletirSemLeitura() {
        when(oddsClient.getRequestsRemaining()).thenReturn(null);
        when(oddsClient.getRequestsUsed()).thenReturn(null);
        when(oddsClient.getUltimaLeitura()).thenReturn(null);
        when(oddsClient.isGuardrailAtivo()).thenReturn(false);

        var oddsCotaService = new OddsCotaService(oddsClient, propriedadesComMinimo(50));
        var resposta = oddsCotaService.buscarCota();

        assertThat(resposta.getSaldoRestante()).isNull();
        assertThat(resposta.getConsumoMes()).isNull();
        assertThat(resposta.getUltimaLeitura()).isNull();
        assertThat(resposta.getUltimaSondagem()).isNull();
        assertThat(resposta.getProximaSondagem()).isNull();
    }

    private OddsProperties propriedadesComMinimo(int minimo) {
        var props = new OddsProperties();
        props.setMinRequestsRemaining(minimo);
        return props;
    }
}
