package com.cartola.odds.service;

import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.request.ConfiguracaoRequest;
import com.cartola.odds.repository.ConfiguracaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfiguracaoService")
class ConfiguracaoServiceTest {

    @Mock ConfiguracaoRepository repository;

    @InjectMocks ConfiguracaoService service;

    @Test
    @DisplayName("deve atualizar regra de defesa sem alterar demais campos")
    void deveAtualizarRegraDefesa() {
        var config = Configuracao.defaults();
        var request = new ConfiguracaoRequest();
        request.setEvitarMesmoClubeDefesa(false);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizar(request);

        assertThat(response.isEvitarMesmoClubeDefesa()).isFalse();
        assertThat(config.isEvitarMesmoClubeDefesa()).isFalse();
        assertThat(config.getFormacaoGol()).isEqualTo(1);
        assertThat(config.getPesoMediaPontos()).isEqualTo(0.40);
    }

    @Test
    @DisplayName("deve atualizar limite de atletas por clube")
    void deveAtualizarLimiteAtletasPorClube() {
        var config = Configuracao.defaults();
        var request = new ConfiguracaoRequest();
        request.setLimiteAtletasPorClube(5);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizar(request);

        assertThat(response.getLimiteAtletasPorClube()).isEqualTo(5);
        assertThat(config.getLimiteAtletasPorClube()).isEqualTo(5);
    }

    @Test
    @DisplayName("deve atualizar budget maximo sem alterar demais campos")
    void deveAtualizarBudgetMaximo() {
        var config = Configuracao.defaults();
        var request = new ConfiguracaoRequest();
        request.setBudgetMaximo(120.0);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizar(request);

        assertThat(response.getBudgetMaximo()).isEqualTo(120.0);
        assertThat(config.getBudgetMaximo()).isEqualTo(120.0);
        assertThat(config.getOddLimite()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("deve atualizar peso do desvio sem alterar demais campos")
    void deveAtualizarPesoDesvio() {
        var config = Configuracao.defaults();
        var request = new ConfiguracaoRequest();
        request.setPesoDesvio(0.10);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizar(request);

        assertThat(response.getPesoDesvio()).isEqualTo(0.10);
        assertThat(config.getPesoDesvio()).isEqualTo(0.10);
        assertThat(config.getPesoMediaPontos()).isEqualTo(0.40);
    }

    @Test
    @DisplayName("reset deve restaurar peso do desvio para o valor padrao")
    void resetDeveRestaurarPesoDesvioPadrao() {
        var config = Configuracao.defaults();
        config.setPesoDesvio(0.50);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resetar();

        assertThat(response.getPesoDesvio()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("reset deve restaurar budget maximo para o valor padrao")
    void resetDeveRestaurarBudgetMaximoPadrao() {
        var config = Configuracao.defaults();
        config.setBudgetMaximo(999.0);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resetar();

        assertThat(response.getBudgetMaximo()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reset deve reativar regra de defesa")
    void resetDeveReativarRegraDefesa() {
        var config = Configuracao.defaults();
        config.setEvitarMesmoClubeDefesa(false);

        when(repository.findById(1L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resetar();

        assertThat(response.isEvitarMesmoClubeDefesa()).isTrue();
        assertThat(config.isEvitarMesmoClubeDefesa()).isTrue();
        assertThat(response.getLimiteAtletasPorClube()).isEqualTo(4);
    }
}
