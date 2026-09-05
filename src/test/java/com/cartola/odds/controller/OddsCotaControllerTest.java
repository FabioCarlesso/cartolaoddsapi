package com.cartola.odds.controller;

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
    @DisplayName("deve refletir guardrailAtivo=true quando o saldo esta abaixo do minimo")
    void deveRefletirGuardrailAtivo() throws Exception {
        when(oddsCotaService.buscarCota()).thenReturn(OddsCotaResponse.builder()
                .saldoRestante(10L)
                .consumoMes(490L)
                .minRequestsRemaining(50)
                .guardrailAtivo(true)
                .build());

        mockMvc.perform(get("/api/odds/cota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guardrailAtivo").value(true));
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
                .andExpect(jsonPath("$.consumoMes").doesNotExist());
    }
}
