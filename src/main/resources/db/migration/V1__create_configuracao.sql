CREATE TABLE configuracao (
    id                  BIGINT        PRIMARY KEY,
    odd_limite          NUMERIC(5,2)  NOT NULL DEFAULT 3.00,
    peso_media_pontos   NUMERIC(5,3)  NOT NULL DEFAULT 0.400,
    peso_valorizacao    NUMERIC(5,3)  NOT NULL DEFAULT 0.200,
    peso_desempenho     NUMERIC(5,3)  NOT NULL DEFAULT 0.200,
    peso_fator_casa     NUMERIC(5,3)  NOT NULL DEFAULT 0.100,
    peso_time_favorito  NUMERIC(5,3)  NOT NULL DEFAULT 0.100,
    formacao_gol        INT           NOT NULL DEFAULT 1,
    formacao_lat        INT           NOT NULL DEFAULT 2,
    formacao_zag        INT           NOT NULL DEFAULT 2,
    formacao_mei        INT           NOT NULL DEFAULT 3,
    formacao_ata        INT           NOT NULL DEFAULT 3,
    formacao_tec        INT           NOT NULL DEFAULT 1,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_single_row CHECK (id = 1)
);

INSERT INTO configuracao (
    id, odd_limite,
    peso_media_pontos, peso_valorizacao, peso_desempenho, peso_fator_casa, peso_time_favorito,
    formacao_gol, formacao_lat, formacao_zag, formacao_mei, formacao_ata, formacao_tec,
    updated_at
) VALUES (
    1, 3.00,
    0.400, 0.200, 0.200, 0.100, 0.100,
    1, 2, 2, 3, 3, 1,
    CURRENT_TIMESTAMP
);
