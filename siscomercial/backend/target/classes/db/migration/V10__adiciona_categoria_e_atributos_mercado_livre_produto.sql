CREATE TABLE produto_mercado_livre_atributo (
    produto_id BIGINT NOT NULL,
    atributo_id VARCHAR(100) NOT NULL,
    nome VARCHAR(255),
    value_id VARCHAR(100),
    value_name TEXT,
    CONSTRAINT fk_produto_mercado_livre_atributo_produto
        FOREIGN KEY (produto_id) REFERENCES produto(id) ON DELETE CASCADE,
    CONSTRAINT pk_produto_mercado_livre_atributo
        PRIMARY KEY (produto_id, atributo_id)
);

CREATE INDEX idx_produto_ml_atributo_produto
    ON produto_mercado_livre_atributo (produto_id);
