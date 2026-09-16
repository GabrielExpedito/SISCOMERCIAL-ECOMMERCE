ALTER TABLE integracao_marketplace
    ADD COLUMN ultima_sincronizacao_pedidos TIMESTAMP;

CREATE TABLE importacao_pedido_marketplace (
    id BIGSERIAL PRIMARY KEY,
    integracao_id BIGINT NOT NULL REFERENCES integracao_marketplace(id),
    identificador_externo VARCHAR(80) NOT NULL,
    status_externo VARCHAR(40) NOT NULL,
    data_criacao_externa TIMESTAMP,
    data_atualizacao_externa TIMESTAMP,
    total NUMERIC(12,2),
    moeda VARCHAR(10),
    estoque_baixado BOOLEAN NOT NULL DEFAULT FALSE,
    importado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    ultimo_erro TEXT,
    CONSTRAINT uk_importacao_pedido_marketplace UNIQUE (integracao_id, identificador_externo)
);

CREATE TABLE importacao_item_pedido_marketplace (
    id BIGSERIAL PRIMARY KEY,
    importacao_id BIGINT NOT NULL REFERENCES importacao_pedido_marketplace(id) ON DELETE CASCADE,
    identificador_item_externo VARCHAR(80) NOT NULL,
    produto_id BIGINT NOT NULL REFERENCES produto(id),
    quantidade INTEGER NOT NULL,
    valor_unitario NUMERIC(12,2),
    estoque_baixado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_importacao_item_marketplace UNIQUE (importacao_id, identificador_item_externo)
);

CREATE INDEX idx_importacao_pedido_marketplace_integracao_status
    ON importacao_pedido_marketplace (integracao_id, status_externo);

CREATE INDEX idx_importacao_item_marketplace_produto
    ON importacao_item_pedido_marketplace (produto_id);
