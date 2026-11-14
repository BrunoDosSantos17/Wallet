package com.brunoSantos.wallet_app.position.dto;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AssetPositionResponseTest {

    @Test
    void separates_historical_cost_from_market_value_for_fractional_quantity() {
        LocalDateTime update = LocalDateTime.of(2026, 11, 14, 10, 15, 30);
        Asset asset = Asset.builder().ticker("PETR4").name("Petrobras").type(AssetType.STOCK)
                .currentPrice(new BigDecimal("40.12345678")).lastUpdate(update).build();
        AssetPosition position = AssetPosition.builder().asset(asset)
                .quantity(new BigDecimal("1.123456789012"))
                .averagePrice(new BigDecimal("30.12345678")).build();

        AssetPositionResponse result = AssetPositionResponse.fromEntity(position);

        assertThat(result.ticker()).isEqualTo("PETR4");
        assertThat(result.assetName()).isEqualTo("Petrobras");
        assertThat(result.assetType()).isEqualTo(AssetType.STOCK);
        assertThat(result.quantity()).isEqualTo(new BigDecimal("1.123456789012"));
        assertThat(result.averagePrice()).isEqualTo(new BigDecimal("30.12345678"));
        assertThat(result.totalCost()).isEqualTo(new BigDecimal("33.84"));
        assertThat(result.currentPrice()).isEqualTo(new BigDecimal("40.12345678"));
        assertThat(result.marketValue()).isEqualTo(new BigDecimal("45.08"));
        assertThat(result.lastPriceUpdate()).isEqualTo(update);
    }

    @Test
    void missing_price_has_null_market_value_and_timestamp_even_if_timestamp_is_stale() {
        Asset asset = Asset.builder().lastUpdate(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        AssetPosition position = AssetPosition.builder().asset(asset).quantity(BigDecimal.TEN)
                .averagePrice(new BigDecimal("5.00")).build();

        AssetPositionResponse result = AssetPositionResponse.fromEntity(position);

        assertThat(result.totalCost()).isEqualTo(new BigDecimal("50.00"));
        assertThat(result.currentPrice()).isNull();
        assertThat(result.marketValue()).isNull();
        assertThat(result.lastPriceUpdate()).isNull();
    }

    @Test
    void zero_position_with_known_price_has_numeric_zero_market_value() {
        Asset asset = Asset.builder().currentPrice(new BigDecimal("42.00")).build();
        AssetPosition position = AssetPosition.builder().asset(asset).quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO).build();

        AssetPositionResponse result = AssetPositionResponse.fromEntity(position);

        assertThat(result.totalCost()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.marketValue()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.lastPriceUpdate()).isNull();
    }

    @Test
    void persisted_zero_price_is_not_a_missing_price() {
        Asset asset = Asset.builder().currentPrice(BigDecimal.ZERO).build();
        AssetPosition position = AssetPosition.builder().asset(asset).quantity(BigDecimal.ONE)
                .averagePrice(BigDecimal.ONE).build();

        AssetPositionResponse result = AssetPositionResponse.fromEntity(position);

        assertThat(result.currentPrice()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.marketValue()).isEqualTo(new BigDecimal("0.00"));
    }
}
