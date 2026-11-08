package com.brunoSantos.wallet_app.transaction.dto;

import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull
        @Positive
        Long walletId,

        @NotBlank
        String ticker,

        @NotNull
        TransactionType type,

        String assetType,
        BigDecimal lastPrice,

        @NotNull
        @Positive
        BigDecimal quantity,

        @NotNull
        @Positive
        BigDecimal price,

        @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "Data deve estar no formato dd/MM/yyyy")
        String date
) {}
