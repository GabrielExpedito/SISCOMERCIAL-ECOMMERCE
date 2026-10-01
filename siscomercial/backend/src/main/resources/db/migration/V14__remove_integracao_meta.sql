-- Remove somente dados vinculados ao canal Meta; preserva integracoes de ML e Shopee.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM publicacao_marketplace p
        JOIN integracao_marketplace i ON i.id = p.integracao_id
        WHERE i.marketplace = 'META'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON r.oid = c.confrelid
        JOIN pg_namespace n ON n.oid = r.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND r.relname = 'integracao_marketplace'
          AND c.conrelid = 'publicacao_marketplace'::regclass
    ) THEN
        RAISE EXCEPTION 'FK publicacao_marketplace.integracao_id não encontrada; remoção Meta cancelada';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM historico_integracao_marketplace h
        JOIN integracao_marketplace i ON i.id = h.integracao_id
        WHERE i.marketplace = 'META'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON r.oid = c.confrelid
        JOIN pg_namespace n ON n.oid = r.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND r.relname = 'integracao_marketplace'
          AND c.conrelid = 'historico_integracao_marketplace'::regclass
    ) THEN
        RAISE EXCEPTION 'FK historico_integracao_marketplace.integracao_id não encontrada; remoção Meta cancelada';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM importacao_pedido_marketplace p
        JOIN integracao_marketplace i ON i.id = p.integracao_id
        WHERE i.marketplace = 'META'
    ) AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON r.oid = c.confrelid
        JOIN pg_namespace n ON n.oid = r.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND r.relname = 'integracao_marketplace'
          AND c.conrelid = 'importacao_pedido_marketplace'::regclass
    ) THEN
        RAISE EXCEPTION 'FK importacao_pedido_marketplace.integracao_id não encontrada; remoção Meta cancelada';
    END IF;
END $$;

DELETE FROM publicacao_marketplace
WHERE integracao_id IN (SELECT id FROM integracao_marketplace WHERE marketplace = 'META');

DELETE FROM historico_integracao_marketplace
WHERE integracao_id IN (SELECT id FROM integracao_marketplace WHERE marketplace = 'META');

DELETE FROM importacao_pedido_marketplace
WHERE integracao_id IN (SELECT id FROM integracao_marketplace WHERE marketplace = 'META');

DELETE FROM integracao_marketplace WHERE marketplace = 'META';

ALTER TABLE produto DROP COLUMN IF EXISTS id_externo_facebook;
