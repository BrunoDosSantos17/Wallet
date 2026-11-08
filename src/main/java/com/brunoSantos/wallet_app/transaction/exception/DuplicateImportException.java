package com.brunoSantos.wallet_app.transaction.exception;

public class DuplicateImportException extends TransactionException {
    public DuplicateImportException() {
        super("Operação duplicada conforme os dados de importação");
    }
}
