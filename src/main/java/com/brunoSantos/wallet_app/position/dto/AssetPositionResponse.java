package com.brunoSantos.wallet_app.position.dto;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record AssetPositionResponse(
        String ticker,
        String assetName,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal totalCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        LocalDateTime lastPriceUpdate
) {
    public static AssetPositionResponse fromEntity(AssetPosition position) {
        BigDecimal totalCost = position.getAveragePrice()
                .multiply(position.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentPrice = position.getAsset().getCurrentPrice();
        BigDecimal marketValue = currentPrice == null ? null : currentPrice
                .multiply(position.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);

        return new AssetPositionResponse(
                position.getAsset().getTicker(),
                position.getAsset().getName(),
                position.getAsset().getType(),
                position.getQuantity(),
                position.getAveragePrice(),
                totalCost,
                currentPrice,
                marketValue,
                currentPrice == null ? null : position.getAsset().getLastUpdate()
        );
    }
}
