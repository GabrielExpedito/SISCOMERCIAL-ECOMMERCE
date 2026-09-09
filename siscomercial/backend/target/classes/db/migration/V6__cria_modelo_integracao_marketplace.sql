CREATE TABLE integracao_marketplace (
    id BIGSERIAL PRIMARY KEY,
    loja_proprietaria VARCHAR(255) NOT NULL,
    marketplace VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    identificador_externo VARCHAR(255) NOT NULL,
    credenciais_protegidas TEXT,
    token_protegido TEXT,
    token_expira_em TIMESTAMP,
    ultima_sincronizacao TIMESTAMP,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_integracao_marketplace_canal_loja UNIQUE (marketplace, identificador_externo)
);

CREATE TABLE publicacao_marketplace (
    id BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    integracao_id BIGINT NOT NULL REFERENCES integracao_marketplace (id),
    identificador_externo VARCHAR(255),
    url_publicacao TEXT,
    status VARCHAR(40) NOT NULL,
    quantidade_publicada INTEGER NOT NULL DEFAULT 0,
    ultima_sincronizacao TIMESTAMP,
    ultimo_erro TEXT,
    CONSTRAINT uk_publicacao_produto_integracao UNIQUE (produto_id, integracao_id)
);

CREATE TABLE historico_integracao_marketplace (
    id BIGSERIAL PRIMARY KEY,
    integracao_id BIGINT NOT NULL REFERENCES integracao_marketplace (id),
    operacao VARCHAR(100) NOT NULL,
    referencia_externa VARCHAR(255),
    status VARCHAR(40) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    mensagem_tecnica TEXT,
    tentativa INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_historico_integracao_marketplace_data
    ON historico_integracao_marketplace (integracao_id, data_hora);
