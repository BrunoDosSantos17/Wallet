package com.brunoSantos.wallet_app.shared.financial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialPrecisionTest {

    @Test
    void rounds_prices_half_up_to_eight_decimal_places() {
        assertThat(FinancialPrecision.normalizePrice(new BigDecimal("12.123456784")))
                .isEqualByComparingTo("12.12345678");
        assertThat(FinancialPrecision.normalizePrice(new BigDecimal("12.123456785")))
                .isEqualByComparingTo("12.12345679");
        assertThat(FinancialPrecision.normalizePrice(new BigDecimal("0.000000005")))
                .isEqualByComparingTo("0.00000001");
    }

    @Test
    void rounds_quantities_half_up_to_twelve_decimal_places() {
        assertThat(FinancialPrecision.normalizeQuantity(new BigDecimal("1.1234567890124")))
                .isEqualByComparingTo("1.123456789012");
        assertThat(FinancialPrecision.normalizeQuantity(new BigDecimal("1.1234567890125")))
                .isEqualByComparingTo("1.123456789013");
    }

    @Test
    void enforces_integer_capacity_after_rounding() {
        assertThatThrownBy(() -> FinancialPrecision.normalizePrice(new BigDecimal("1e30")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preço");
        assertThatThrownBy(() -> FinancialPrecision.normalizeQuantity(new BigDecimal("1e26")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantidade");
    }
}
