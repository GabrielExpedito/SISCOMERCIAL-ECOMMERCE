ALTER TABLE cliente
ADD COLUMN google_id VARCHAR(255);

ALTER TABLE cliente
ADD CONSTRAINT uk_cliente_google_id UNIQUE (google_id);