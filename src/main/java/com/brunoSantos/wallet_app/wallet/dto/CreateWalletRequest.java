package com.brunoSantos.wallet_app.wallet.dto;


import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotNull String name
) {
}
