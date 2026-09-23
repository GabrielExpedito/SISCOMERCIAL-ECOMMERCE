ALTER TABLE produto
    ADD COLUMN categoria_shopee_id VARCHAR(40);

ALTER TABLE produto
    ADD COLUMN peso_shopee_kg NUMERIC(10,3);
