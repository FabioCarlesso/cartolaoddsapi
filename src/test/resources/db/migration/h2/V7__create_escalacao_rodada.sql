CREATE TABLE escalacao_rodada (
    id                  BIGSERIAL        PRIMARY KEY,
    rodada_id           INTEGER          NOT NULL,
    atleta_id           INTEGER          NOT NULL,
    apelido             VARCHAR(100)     NOT NULL,
    posicao             VARCHAR(10)      NOT NULL,
    clube               VARCHAR(100)     NOT NULL,
    score_sugerido      DOUBLE PRECISION NOT NULL,
    preco               DOUBLE PRECISION NOT NULL,
    capitao             BOOLEAN          NOT NULL DEFAULT FALSE,
    reserva_luxo        BOOLEAN          NOT NULL DEFAULT FALSE,
    em_duvida           BOOLEAN          NOT NULL DEFAULT FALSE,
    pontuacao_real      DOUBLE PRECISION,
    criado_em           TIMESTAMP        NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_escalacao_rodada_atleta UNIQUE (rodada_id, atleta_id)
);

CREATE INDEX idx_escalacao_rodada_id ON escalacao_rodada (rodada_id);
