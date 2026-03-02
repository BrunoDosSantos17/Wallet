package com.brunoSantos.wallet_app.wallet.dto;

import com.brunoSantos.wallet_app.position.dto.AssetPositionResponse;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;

import java.util.List;

public record WalletResponse(Long id,
                             String name,
                             List<AssetPositionResponse>assetPositions
) {

    public static WalletResponse fromEntity(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getPositions() == null
                        ? List.of()
                        : wallet.getPositions()
                        .stream()
                        .map(AssetPositionResponse::fromEntity)
                        .toList()
        );
    }
}
