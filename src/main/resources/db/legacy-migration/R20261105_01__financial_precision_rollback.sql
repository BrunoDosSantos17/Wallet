-- Manual rollback for V20261105_01__financial_precision.sql.
-- Do not run unless the preflight passes and a verified backup is available.
-- Higher-scale values are preserved: this script aborts instead of rounding them.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM asset WHERE current_price <> trunc(current_price, 2)) THEN
        RAISE EXCEPTION 'C5 rollback blocked: asset.current_price has more than two fractional digits';
    END IF;
    IF EXISTS (SELECT 1 FROM asset_position WHERE average_price <> trunc(average_price, 2)) THEN
        RAISE EXCEPTION 'C5 rollback blocked: asset_position.average_price has more than two fractional digits';
    END IF;
    IF EXISTS (SELECT 1 FROM asset_position WHERE quantity <> trunc(quantity, 2)) THEN
        RAISE EXCEPTION 'C5 rollback blocked: asset_position.quantity has more than two fractional digits';
    END IF;
    IF EXISTS (SELECT 1 FROM "transaction" WHERE price <> trunc(price, 2)) THEN
        RAISE EXCEPTION 'C5 rollback blocked: transaction.price has more than two fractional digits';
    END IF;
    IF EXISTS (SELECT 1 FROM "transaction" WHERE quantity <> trunc(quantity, 2)) THEN
        RAISE EXCEPTION 'C5 rollback blocked: transaction.quantity has more than two fractional digits';
    END IF;
END
$$;

ALTER TABLE asset
    ALTER COLUMN current_price TYPE NUMERIC(38, 2);

ALTER TABLE asset_position
    ALTER COLUMN quantity TYPE NUMERIC(38, 2),
    ALTER COLUMN average_price TYPE NUMERIC(38, 2);

ALTER TABLE "transaction"
    ALTER COLUMN quantity TYPE NUMERIC(38, 2),
    ALTER COLUMN price TYPE NUMERIC(38, 2);
