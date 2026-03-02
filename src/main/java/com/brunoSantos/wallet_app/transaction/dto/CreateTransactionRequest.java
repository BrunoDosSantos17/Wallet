package com.brunoSantos.wallet_app.transaction.dto;

import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        Long walletId,
        String ticker,
        TransactionType type,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal quantity,

        @NotNull
        @DecimalMin("0.001")
        BigDecimal price
) {
}
