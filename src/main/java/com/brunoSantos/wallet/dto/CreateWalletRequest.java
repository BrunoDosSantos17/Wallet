package com.brunoSantos.wallet.dto;


import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(
        @NotNull String name
) {
}
