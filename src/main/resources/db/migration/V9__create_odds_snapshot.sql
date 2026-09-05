CREATE TABLE odds_snapshot (
    id         BIGINT    PRIMARY KEY,
    odds_json  TEXT      NOT NULL,
    criado_em  TIMESTAMP NOT NULL
);
-- Linha unica (id sempre 1): guarda a ultima resposta de odds bem-sucedida da The Odds
-- API, para o guardrail de cota (#40) servir fallback que sobrevive a restart e redeploy,
-- em vez de depender so do cache Caffeine em memoria.
