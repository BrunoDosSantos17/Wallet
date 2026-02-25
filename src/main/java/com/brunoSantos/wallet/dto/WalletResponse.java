package com.brunoSantos.wallet.dto;

import com.brunoSantos.wallet.domain.Wallet;

public record WalletResponse(Long id,
                             String name) {

    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName()
        );
    }
}
