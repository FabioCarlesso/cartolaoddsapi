ALTER TABLE configuracao
    ALTER COLUMN odd_limite         TYPE DOUBLE PRECISION USING odd_limite::DOUBLE PRECISION,
    ALTER COLUMN peso_media_pontos  TYPE DOUBLE PRECISION USING peso_media_pontos::DOUBLE PRECISION,
    ALTER COLUMN peso_valorizacao   TYPE DOUBLE PRECISION USING peso_valorizacao::DOUBLE PRECISION,
    ALTER COLUMN peso_desempenho    TYPE DOUBLE PRECISION USING peso_desempenho::DOUBLE PRECISION,
    ALTER COLUMN peso_fator_casa    TYPE DOUBLE PRECISION USING peso_fator_casa::DOUBLE PRECISION,
    ALTER COLUMN peso_time_favorito TYPE DOUBLE PRECISION USING peso_time_favorito::DOUBLE PRECISION;
