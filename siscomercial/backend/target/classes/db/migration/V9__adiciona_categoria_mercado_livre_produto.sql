ALTER TABLE produto
    ADD COLUMN categoria_mercado_livre_id VARCHAR(40),
    ADD COLUMN categoria_mercado_livre_nome VARCHAR(255);

CREATE INDEX idx_produto_categoria_mercado_livre_id
    ON produto (categoria_mercado_livre_id);
