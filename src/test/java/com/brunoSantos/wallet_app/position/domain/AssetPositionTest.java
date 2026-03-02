package com.brunoSantos.wallet_app.position.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class AssetPositionTest {
    private AssetPosition position;

    @BeforeEach
    void setUp() {
        position = AssetPosition.builder()
                .wallet(null)
                .asset(null)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .build();
    }

    @Test
    void should_buy_and_update_quantity_and_average_price() {
        position.buy(BigDecimal.valueOf(10), BigDecimal.valueOf(100));

        assertThat(position.getQuantity())
                .isEqualByComparingTo("10");

        assertThat(position.getAveragePrice())
                .isEqualByComparingTo("100");
    }

    @Test
    void should_recalculate_average_price_when_buying_more() {
        position.buy(BigDecimal.valueOf(10), BigDecimal.valueOf(100));
        position.buy(BigDecimal.valueOf(10), BigDecimal.valueOf(200));

        // ((10*100)+(10*200)) / 20 = 150
        assertThat(position.getQuantity())
                .isEqualByComparingTo("20");

        assertThat(position.getAveragePrice())
                .isEqualByComparingTo("150");
    }

    @Test
    void should_sell_and_reduce_quantity() {
        position.buy(BigDecimal.valueOf(10), BigDecimal.valueOf(100));
        position.sell(BigDecimal.valueOf(4));

        assertThat(position.getQuantity())
                .isEqualByComparingTo("6");

        // preço médio não muda ao vender
        assertThat(position.getAveragePrice())
                .isEqualByComparingTo("100");
    }

    @Test
    void should_zero_position_when_selling_all() {
        position.buy(BigDecimal.valueOf(10), BigDecimal.valueOf(100));
        position.sell(BigDecimal.valueOf(10));

        assertThat(position.getQuantity())
                .isEqualByComparingTo("0");

        assertThat(position.getAveragePrice())
                .isEqualByComparingTo("0");
    }
    @Test
    void should_throw_exception_when_selling_more_than_owned() {
        position.buy(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        assertThatThrownBy(() ->
                position.sell(BigDecimal.valueOf(10))
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot sell");
    }

    @Test
    void should_throw_exception_when_buying_negative_quantity() {
        assertThatThrownBy(() ->
                position.buy(BigDecimal.valueOf(-5), BigDecimal.valueOf(100))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_exception_when_selling_negative_quantity() {
        position.buy(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        assertThatThrownBy(() ->
                position.sell(BigDecimal.valueOf(-1))
        ).isInstanceOf(IllegalArgumentException.class);
    }


}