CREATE TABLE historico_status_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    status_anterior VARCHAR(40),
    novo_status VARCHAR(40) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    origem VARCHAR(40) NOT NULL,
    responsavel VARCHAR(255),
    CONSTRAINT fk_historico_status_pedido_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedido (id)
);

CREATE INDEX idx_historico_status_pedido_pedido_data
    ON historico_status_pedido (pedido_id, data_hora);
