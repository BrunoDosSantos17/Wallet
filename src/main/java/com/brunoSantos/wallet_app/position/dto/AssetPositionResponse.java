package com.brunoSantos.wallet_app.position.dto;

import com.brunoSantos.wallet_app.position.domain.AssetPosition;

import java.math.BigDecimal;

public record AssetPositionResponse(
        String ticker,
        BigDecimal quantity,
        BigDecimal averagePrice
) {

    public static AssetPositionResponse fromEntity(AssetPosition position) {
        return new AssetPositionResponse(
                position.getAsset().getTicker(),
                position.getQuantity(),
                position.getAveragePrice()
        );
    }
}
