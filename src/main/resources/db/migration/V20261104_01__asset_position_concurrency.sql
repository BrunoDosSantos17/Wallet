-- Auditoria prévia em 2026-11-04: zero grupos duplicados por (wallet_id, asset_ticker).
-- Aplicar manualmente em janela controlada; não é executado automaticamente pelo projeto.
ALTER TABLE asset_position
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE asset_position
    ADD CONSTRAINT uk_asset_position_wallet_asset UNIQUE (wallet_id, asset_ticker);
