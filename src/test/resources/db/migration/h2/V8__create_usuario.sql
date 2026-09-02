CREATE TABLE usuario (
    id             BIGSERIAL     PRIMARY KEY,
    nome           VARCHAR(120)  NOT NULL,
    email          VARCHAR(180)  NOT NULL,
    senha          VARCHAR(255)  NOT NULL,
    perfil         VARCHAR(30)   NOT NULL,
    ativo          BOOLEAN       NOT NULL DEFAULT TRUE,
    token_version  BIGINT        NOT NULL DEFAULT 0,
    criado_em      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usuario_email UNIQUE (email)
);
-- A UNIQUE (email) ja cria o indice usado no login, que busca sempre por email —
-- nenhum indice adicional e necessario.
