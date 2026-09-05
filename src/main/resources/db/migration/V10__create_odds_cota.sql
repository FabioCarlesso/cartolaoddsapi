CREATE TABLE odds_cota (
    id                 BIGINT    PRIMARY KEY,
    requests_remaining BIGINT,
    requests_used      BIGINT,
    ultima_leitura     TIMESTAMP,
    ultima_sondagem    TIMESTAMP
);
-- Linha unica (id sempre 1): ultimo estado conhecido da cota da The Odds API. O snapshot de
-- odds (V9) ja sobrevivia ao redeploy, mas o saldo que decide se vale a pena chamar vivia so
-- em memoria — cada deploy desarmava o guardrail (#40) ate a primeira chamada bem-sucedida.
-- Colunas anulaveis de proposito: representam "nenhuma leitura ainda".
