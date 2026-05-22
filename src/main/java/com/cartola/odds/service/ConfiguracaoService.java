package com.cartola.odds.service;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.request.ConfiguracaoRequest;
import com.cartola.odds.model.response.ConfiguracaoResponse;
import com.cartola.odds.repository.ConfiguracaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracaoService {

    private final ConfiguracaoRepository repository;

    @Cacheable(CacheConfig.CACHE_CONFIGURACAO)
    public Configuracao buscarConfig() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Configuracao nao encontrada. Execute a migracao do banco de dados."));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_CONFIGURACAO, allEntries = true)
    public ConfiguracaoResponse atualizar(ConfiguracaoRequest request) {
        Configuracao config = repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Configuracao nao encontrada."));

        aplicarAlteracoes(config, request);
        validarPesos(config);

        config.setUpdatedAt(LocalDateTime.now());
        log.info("Configuracao atualizada: oddLimite={} | pesos={}/{}/{}/{}/{}",
                config.getOddLimite(),
                config.getPesoMediaPontos(), config.getPesoValorizacao(),
                config.getPesoDesempenho(), config.getPesoFatorCasa(),
                config.getPesoTimeFavorito());
        return ConfiguracaoResponse.from(repository.save(config));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_CONFIGURACAO, allEntries = true)
    public ConfiguracaoResponse resetar() {
        Configuracao config = repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Configuracao nao encontrada."));

        aplicarDefaults(config);
        config.setUpdatedAt(LocalDateTime.now());
        log.info("Configuracao resetada para os valores padrao.");
        return ConfiguracaoResponse.from(repository.save(config));
    }

    private void aplicarAlteracoes(Configuracao config, ConfiguracaoRequest req) {
        if (req.getOddLimite()        != null) config.setOddLimite(req.getOddLimite());
        if (req.getPesoMediaPontos()  != null) config.setPesoMediaPontos(req.getPesoMediaPontos());
        if (req.getPesoValorizacao()  != null) config.setPesoValorizacao(req.getPesoValorizacao());
        if (req.getPesoDesempenho()   != null) config.setPesoDesempenho(req.getPesoDesempenho());
        if (req.getPesoFatorCasa()    != null) config.setPesoFatorCasa(req.getPesoFatorCasa());
        if (req.getPesoTimeFavorito() != null) config.setPesoTimeFavorito(req.getPesoTimeFavorito());
        if (req.getPesoDesvio()       != null) config.setPesoDesvio(req.getPesoDesvio());
        if (req.getFormacaoGol()      != null) config.setFormacaoGol(req.getFormacaoGol());
        if (req.getFormacaoLat()      != null) config.setFormacaoLat(req.getFormacaoLat());
        if (req.getFormacaoZag()      != null) config.setFormacaoZag(req.getFormacaoZag());
        if (req.getFormacaoMei()      != null) config.setFormacaoMei(req.getFormacaoMei());
        if (req.getFormacaoAta()      != null) config.setFormacaoAta(req.getFormacaoAta());
        if (req.getFormacaoTec()      != null) config.setFormacaoTec(req.getFormacaoTec());
        if (req.getEvitarMesmoClubeDefesa() != null) {
            config.setEvitarMesmoClubeDefesa(req.getEvitarMesmoClubeDefesa());
        }
        if (req.getLimiteAtletasPorClube() != null) {
            config.setLimiteAtletasPorClube(req.getLimiteAtletasPorClube());
        }
        if (req.getBudgetMaximo() != null) {
            config.setBudgetMaximo(req.getBudgetMaximo());
        }
    }

    private void validarPesos(Configuracao config) {
        double soma = config.getPesoMediaPontos()
                    + config.getPesoValorizacao()
                    + config.getPesoDesempenho()
                    + config.getPesoFatorCasa()
                    + config.getPesoTimeFavorito();
        if (Math.abs(soma - 1.0) > 0.01) {
            throw new IllegalArgumentException(
                    "A soma dos pesos deve ser 1.0. Soma atual: %.3f".formatted(soma));
        }
    }

    private void aplicarDefaults(Configuracao config) {
        config.setOddLimite(3.0);
        config.setPesoMediaPontos(0.40);
        config.setPesoValorizacao(0.20);
        config.setPesoDesempenho(0.20);
        config.setPesoFatorCasa(0.10);
        config.setPesoTimeFavorito(0.10);
        config.setPesoDesvio(0.05);
        config.setFormacaoGol(1);
        config.setFormacaoLat(2);
        config.setFormacaoZag(2);
        config.setFormacaoMei(3);
        config.setFormacaoAta(3);
        config.setFormacaoTec(1);
        config.setEvitarMesmoClubeDefesa(true);
        config.setLimiteAtletasPorClube(4);
        config.setBudgetMaximo(0.0);
    }
}
