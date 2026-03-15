package com.brunoSantos.wallet_app.integration.brapi.dto;

import java.math.BigDecimal;

public record BrapiStock(
        String symbol,
        BigDecimal regularMarketPrice
) {
}
