CREATE TABLE IF NOT EXISTS produto_imagem (
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    url VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_produto_imagem_produto
    ON produto_imagem (produto_id);
