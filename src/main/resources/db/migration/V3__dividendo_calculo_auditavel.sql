ALTER TABLE dividendo_recebido ADD COLUMN data_base DATE;
ALTER TABLE dividendo_recebido ADD COLUMN quantidade_elegivel NUMERIC(38, 12);
ALTER TABLE dividendo_recebido ADD COLUMN valor_unitario NUMERIC(38, 12);
ALTER TABLE dividendo_recebido ADD COLUMN assinatura_evento VARCHAR(64);
