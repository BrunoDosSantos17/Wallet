ALTER TABLE dividendo_recebido ADD COLUMN moeda VARCHAR(3) NOT NULL DEFAULT 'BRL';
ALTER TABLE dividendo_recebido ADD CONSTRAINT ck_dividendo_moeda CHECK (moeda IN ('BRL', 'USD'));
