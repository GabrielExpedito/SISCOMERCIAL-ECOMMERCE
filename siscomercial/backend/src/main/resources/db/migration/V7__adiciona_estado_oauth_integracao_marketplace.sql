ALTER TABLE integracao_marketplace ADD COLUMN oauth_state VARCHAR(120);
ALTER TABLE integracao_marketplace ADD COLUMN oauth_state_expira_em TIMESTAMP;
