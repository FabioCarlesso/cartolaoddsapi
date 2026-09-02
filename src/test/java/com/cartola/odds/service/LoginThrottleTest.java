package com.cartola.odds.service;

import com.cartola.odds.config.LoginProperties;
import com.cartola.odds.exception.TentativasExcedidasException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LoginThrottle")
class LoginThrottleTest {

    private static final String EMAIL = "admin@cartolaodds.local";

    private LoginThrottle throttle(int maxTentativas) {
        var props = new LoginProperties();
        props.setMaxTentativas(maxTentativas);
        props.setJanelaMinutos(5);
        return new LoginThrottle(props);
    }

    private void falhar(LoginThrottle throttle, String email, int vezes) {
        for (int i = 0; i < vezes; i++) {
            throttle.registrarFalha(email);
        }
    }

    @Test
    @DisplayName("deve liberar o login enquanto o limite nao e atingido")
    void deveLiberarAbaixoDoLimite() {
        var throttle = throttle(5);
        falhar(throttle, EMAIL, 4);

        assertThatCode(() -> throttle.verificar(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deve bloquear ao atingir o limite de tentativas")
    void deveBloquearNoLimite() {
        var throttle = throttle(5);
        falhar(throttle, EMAIL, 5);

        assertThatThrownBy(() -> throttle.verificar(EMAIL))
                .isInstanceOf(TentativasExcedidasException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    @DisplayName("deve zerar a contagem apos um login bem-sucedido")
    void deveZerarAposSucesso() {
        var throttle = throttle(5);
        falhar(throttle, EMAIL, 5);

        throttle.registrarSucesso(EMAIL);

        assertThatCode(() -> throttle.verificar(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deve contar por e-mail, sem bloquear os demais usuarios")
    void deveContarPorEmail() {
        var throttle = throttle(5);
        falhar(throttle, EMAIL, 5);

        assertThatCode(() -> throttle.verificar("outro@cartolaodds.local")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deve normalizar caixa e espacos, para que alternar a grafia nao ganhe janela nova")
    void deveNormalizarEmail() {
        var throttle = throttle(3);
        throttle.registrarFalha("Admin@CartolaOdds.local");
        throttle.registrarFalha("  admin@cartolaodds.local  ");
        throttle.registrarFalha("ADMIN@CARTOLAODDS.LOCAL");

        assertThatThrownBy(() -> throttle.verificar(EMAIL))
                .isInstanceOf(TentativasExcedidasException.class);
    }

    @Test
    @DisplayName("deve informar a janela de espera na mensagem de erro")
    void deveInformarJanelaNaMensagem() {
        var throttle = throttle(1);
        falhar(throttle, EMAIL, 1);

        assertThatThrownBy(() -> throttle.verificar(EMAIL)).hasMessageContaining("5 minutos");
    }
}
