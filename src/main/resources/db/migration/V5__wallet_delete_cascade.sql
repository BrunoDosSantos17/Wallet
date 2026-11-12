-- Apagar uma carteira remove seu histórico, mas nunca os ativos compartilhados.
ALTER TABLE asset_position DROP CONSTRAINT asset_position_wallet_id_fkey;
ALTER TABLE asset_position ADD CONSTRAINT asset_position_wallet_id_fkey
    FOREIGN KEY (wallet_id) REFERENCES wallet(id) ON DELETE CASCADE;

ALTER TABLE "transaction" DROP CONSTRAINT transaction_wallet_id_fkey;
ALTER TABLE "transaction" ADD CONSTRAINT transaction_wallet_id_fkey
    FOREIGN KEY (wallet_id) REFERENCES wallet(id) ON DELETE CASCADE;
