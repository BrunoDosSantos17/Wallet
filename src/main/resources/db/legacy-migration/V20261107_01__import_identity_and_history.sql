-- C4: manual migration. Take a verified backup, audit legacy history and suspend
-- writes first. Existing rows keep a NULL import_identity: they cannot safely be
-- deduplicated retroactively without knowing their origin and normalized inputs.
BEGIN;
LOCK TABLE "transaction" IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM "transaction" WHERE data_transaction IS NULL) THEN
        RAISE EXCEPTION 'C4 blocked: historical transactions have no operation date; audit and resolve first';
    END IF;
END $$;
ALTER TABLE "transaction" ADD COLUMN import_identity VARCHAR(64);
CREATE UNIQUE INDEX uk_transaction_wallet_import_identity
    ON "transaction" (wallet_id, import_identity) WHERE import_identity IS NOT NULL;
CREATE INDEX ix_transaction_wallet_asset_history
    ON "transaction" (wallet_id, asset_ticker, data_transaction);
COMMIT;
