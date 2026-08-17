package com.brunoSantos.wallet_app.position.dto;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import java.math.BigDecimal;

public record AssetPositionResponse(
        String ticker,
        String assetName,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal totalCost
) {
    public static AssetPositionResponse fromEntity(AssetPosition position) {
        BigDecimal totalCost = position.getAveragePrice()
                .multiply(position.getQuantity());

        return new AssetPositionResponse(
                position.getAsset().getTicker(),
                position.getAsset().getName(),
                position.getAsset().getType(),
                position.getQuantity(),
                position.getAveragePrice(),
                totalCost
        );
    }
}