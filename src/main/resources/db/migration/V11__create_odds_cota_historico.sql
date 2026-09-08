CREATE TABLE odds_cota_historico (
    id              BIGSERIAL PRIMARY KEY,
    instante        TIMESTAMP NOT NULL,
    saldo_restante  BIGINT,
    consumo_mes     BIGINT
);
CREATE INDEX idx_odds_cota_historico_instante ON odds_cota_historico (instante);
-- Append-only: uma linha por leitura de header de cota da The Odds API. A odds_cota (V10)
-- continua sendo a linha unica com o estado corrente, que e o que o guardrail recupera no
-- boot; aqui fica a serie, que e o que permite desenhar o consumo ao longo do mes sem
-- depender de um Prometheus ao lado (#61).
--
-- Nao ha retencao, e e uma decisao: uma linha so nasce de uma chamada ao provedor, e as
-- chamadas sao limitadas pela propria cota que a tabela mede — no plano free, no maximo ~500
-- linhas por mes. Uma politica de expurgo custaria mais atencao do que o espaco que economiza.
--
-- Colunas de valor anulaveis pelo mesmo motivo da V10: uma leitura pode trazer so um dos dois
-- headers, e gravar zero no lugar do ausente inventaria um dado que o provedor nao mandou.
