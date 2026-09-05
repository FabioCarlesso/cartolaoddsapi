package com.cartola.odds.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As propriedades do guardrail sao variaveis de ambiente independentes, e algumas combinacoes
 * nao descrevem nenhuma configuracao valida — so quebram alguma conta la na frente. Recusar no
 * boot custa um restart e diz qual propriedade esta errada; aceitar custaria erro em toda
 * requisicao de odds, ja em producao e depois de o credito ter sido gasto.
 */
@DisplayName("OddsProperties — validacao no boot")
class OddsPropertiesValidacaoTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(OddsProperties.class);

    @Test
    @DisplayName("deve subir com os valores padrao")
    void deveSubirComPadroes() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var props = context.getBean(OddsProperties.class);
            assertThat(props.getCacheTtlMinutos()).isEqualTo(60);
            assertThat(props.getCacheTtlDegradadoMinutos()).isEqualTo(10);
            assertThat(props.getMinRequestsRemaining()).isEqualTo(50);
            assertThat(props.getSondaIntervaloHoras()).isEqualTo(24);
        });
    }

    @Test
    @DisplayName("deve recusar TTL degradado maior que o TTL cheio, nomeando as duas propriedades")
    void deveRecusarTtlDegradadoMaiorQueCheio() {
        // O degradado e um piso dentro do cheio: invertidos, o calculo de validade do cache
        // recebe um intervalo de cabeca para baixo.
        runner.withPropertyValues(
                        "odds.api.cache-ttl-minutos=5",
                        "odds.api.cache-ttl-degradado-minutos=10")
                .run(context -> {
                    assertThat(context).hasFailed();
                    // Na cadeia de causas, e nao na mensagem do topo: e o que o operador le no
                    // log de boot, e a graca de recusar aqui e justamente dizer qual propriedade
                    // esta errada em vez de um "erro ao criar bean" generico.
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("cache-ttl-degradado-minutos")
                            .hasStackTraceContaining("cache-ttl-minutos");
                });
    }

    @Test
    @DisplayName("deve aceitar TTL degradado igual ao cheio")
    void deveAceitarTtlsIguais() {
        runner.withPropertyValues(
                        "odds.api.cache-ttl-minutos=10",
                        "odds.api.cache-ttl-degradado-minutos=10")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("deve recusar intervalo de sondagem zerado, que desligaria o guardrail na pratica")
    void deveRecusarSondagemZerada() {
        // Com 0, toda requisicao vira sondagem: o guardrail continua "ativo" no relatorio e
        // gasta exatamente a cota que existe para preservar.
        runner.withPropertyValues("odds.api.sonda-intervalo-horas=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("deve recusar TTL de cache zerado ou negativo")
    void deveRecusarTtlInvalido() {
        runner.withPropertyValues("odds.api.cache-ttl-minutos=0")
                .run(context -> assertThat(context).hasFailed());

        runner.withPropertyValues("odds.api.cache-ttl-degradado-minutos=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("deve recusar minimo de requisicoes zerado, que so armaria o guardrail com a cota no fim")
    void deveRecusarMinimoZerado() {
        runner.withPropertyValues("odds.api.min-requests-remaining=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
