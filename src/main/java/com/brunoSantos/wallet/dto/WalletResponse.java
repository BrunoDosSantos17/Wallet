package com.brunoSantos.wallet.dto;

import com.brunoSantos.position.domain.AssetPosition;
import com.brunoSantos.position.dto.AssetPositionResponse;
import com.brunoSantos.wallet.domain.Wallet;

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
