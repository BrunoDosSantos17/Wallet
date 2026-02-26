package com.brunoSantos.transaction.dto;

import com.brunoSantos.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        String nameWallet,
        String nameAsset,
        BigDecimal quantity,
        BigDecimal price,
        LocalDate date

) {
    public static TransactionResponse fromEntity(Transaction transaction) {
        return new TransactionResponse(
                transaction.getWallet().getName(),
                transaction.getAsset().getName(),
                transaction.getQuantity(),
                transaction.getPrice(),
                transaction.getDate()
        );
    }

}
