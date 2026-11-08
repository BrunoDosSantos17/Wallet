-- C5-BE: precision/scale approved on 2026-11-05.
-- Read-only audit of wallet/public found NUMERIC(38,2) in these columns and zero
-- rows in asset, asset_position and transaction at audit time.
-- Apply manually only after a fresh audit and a verified backup; never auto-run.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM asset WHERE abs(current_price) >= 1e30) THEN
        RAISE EXCEPTION 'C5 migration blocked: asset.current_price exceeds NUMERIC(38,8) integer capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM asset_position WHERE abs(average_price) >= 1e30) THEN
        RAISE EXCEPTION 'C5 migration blocked: asset_position.average_price exceeds NUMERIC(38,8) integer capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM asset_position WHERE abs(quantity) >= 1e26) THEN
        RAISE EXCEPTION 'C5 migration blocked: asset_position.quantity exceeds NUMERIC(38,12) integer capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM "transaction" WHERE abs(price) >= 1e30) THEN
        RAISE EXCEPTION 'C5 migration blocked: transaction.price exceeds NUMERIC(38,8) integer capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM "transaction" WHERE abs(quantity) >= 1e26) THEN
        RAISE EXCEPTION 'C5 migration blocked: transaction.quantity exceeds NUMERIC(38,12) integer capacity';
    END IF;
END
$$;

ALTER TABLE asset
    ALTER COLUMN current_price TYPE NUMERIC(38, 8);

ALTER TABLE asset_position
    ALTER COLUMN quantity TYPE NUMERIC(38, 12),
    ALTER COLUMN average_price TYPE NUMERIC(38, 8);

ALTER TABLE "transaction"
    ALTER COLUMN quantity TYPE NUMERIC(38, 12),
    ALTER COLUMN price TYPE NUMERIC(38, 8);
