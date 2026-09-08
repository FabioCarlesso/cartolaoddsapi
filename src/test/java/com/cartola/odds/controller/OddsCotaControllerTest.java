package com.cartola.odds.controller;

import com.cartola.odds.model.response.OddsCotaHistoricoResponse;
import com.cartola.odds.model.response.OddsCotaResponse;
import com.cartola.odds.service.OddsCotaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OddsCotaController.class)
// Filtros de seguranca desligados: estes testes verificam o comportamento da controller,
// e a matriz de quem acessa o que tem cobertura propria em PoliticaAcessoIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OddsCotaController")
class OddsCotaControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OddsCotaService oddsCotaService;

    @Test
    @DisplayName("deve retornar 200 com saldo, consumo, ultima leitura e guardrail")
    void deveRetornarCotaAtual() throws Exception {
        var agora = LocalDateTime.of(2026, 9, 5, 10, 0);
        when(oddsCotaService.buscarCota()).thenReturn(OddsCotaResponse.builder()
                .saldoRestante(412L)
                .consumoMes(88L)
                .ultimaLeitura(agora)
                .minRequestsRemaining(50)
                .guardrailAtivo(false)
                .build());

        mockMvc.perform(get("/api/odds/cota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoRestante").value(412))
                .andExpect(jsonPath("$.consumoMes").value(88))
                .andExpect(jsonPath("$.minRequestsRemaining").value(50))
                .andExpect(jsonPath("$.guardrailAtivo").value(false));
    }

    @Test
    @DisplayName("deve refletir guardrailAtivo=true e dizer quando a proxima sondagem destrava")
    void deveRefletirGuardrailAtivo() throws Exception {
        var proxima = LocalDateTime.of(2026, 9, 6, 10, 0);
        when(oddsCotaService.buscarCota()).thenReturn(OddsCotaResponse.builder()
                .saldoRestante(10L)
                .consumoMes(490L)
                .minRequestsRemaining(50)
                .guardrailAtivo(true)
                .ultimaSondagem(LocalDateTime.of(2026, 9, 5, 10, 0))
                .proximaSondagem(proxima)
                .build());

        mockMvc.perform(get("/api/odds/cota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardrailAtivo").value(true))
                .andExpect(jsonPath("$.ultimaSondagem").value("2026-09-05T10:00:00"))
                .andExpect(jsonPath("$.proximaSondagem").value("2026-09-06T10:00:00"));
    }

    @Test
    @DisplayName("deve retornar campos nulos quando ainda nao houve leitura de cota")
    void deveRetornarNuloSemLeitura() throws Exception {
        when(oddsCotaService.buscarCota()).thenReturn(OddsCotaResponse.builder()
                .minRequestsRemaining(50)
                .guardrailAtivo(false)
                .build());

        mockMvc.perform(get("/api/odds/cota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoRestante").doesNotExist())
                .andExpect(jsonPath("$.consumoMes").doesNotExist())
                .andExpect(jsonPath("$.proximaSondagem").doesNotExist());
    }

    @Test
    @DisplayName("deve retornar a serie do historico com a marca de reinicio de cota")
    void deveRetornarHistorico() throws Exception {
        var base = LocalDateTime.of(2026, 9, 1, 10, 0);
        when(oddsCotaService.buscarHistorico(30)).thenReturn(OddsCotaHistoricoResponse.builder()
                .dias(30)
                .desde(base)
                .total(2)
                .leituras(List.of(
                        OddsCotaHistoricoResponse.LeituraCotaDto.builder()
                                .instante(base).saldoRestante(8L).consumoMes(492L)
                                .reinicioDeCota(false).build(),
                        OddsCotaHistoricoResponse.LeituraCotaDto.builder()
                                .instante(base.plusHours(1)).saldoRestante(500L).consumoMes(0L)
                                .reinicioDeCota(true).build()))
                .build());

        mockMvc.perform(get("/api/odds/cota/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(30))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.leituras[0].saldoRestante").value(8))
                .andExpect(jsonPath("$.leituras[0].reinicioDeCota").value(false))
                .andExpect(jsonPath("$.leituras[1].consumoMes").value(0))
                .andExpect(jsonPath("$.leituras[1].reinicioDeCota").value(true));
    }

    @Test
    @DisplayName("deve repassar a janela informada em ?dias")
    void deveRepassarJanela() throws Exception {
        when(oddsCotaService.buscarHistorico(7)).thenReturn(OddsCotaHistoricoResponse.builder()
                .dias(7).total(0).leituras(List.of()).build());

        mockMvc.perform(get("/api/odds/cota/historico").param("dias", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(7))
                .andExpect(jsonPath("$.leituras").isEmpty());
    }

    @Test
    @DisplayName("deve traduzir janela invalida para 400, e nao 500")
    void deveTraduzirJanelaInvalida() throws Exception {
        when(oddsCotaService.buscarHistorico(anyInt()))
                .thenThrow(new IllegalArgumentException("dias deve ser maior que 0. Valor informado: 0"));

        mockMvc.perform(get("/api/odds/cota/historico").param("dias", "0"))
                .andExpect(status().isBadRequest());
    }
}
