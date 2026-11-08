package com.brunoSantos.wallet_app.shared.financial;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared persistence precision and rounding policy for financial values. */
public final class FinancialPrecision {

    public static final int PRICE_PRECISION = 38;
    public static final int PRICE_SCALE = 8;
    public static final int QUANTITY_PRECISION = 38;
    public static final int QUANTITY_SCALE = 12;
    public static final int AVERAGE_PRICE_SCALE = 8;

    private static final BigDecimal MAX_PRICE_EXCLUSIVE = BigDecimal.TEN.pow(PRICE_PRECISION - PRICE_SCALE);
    private static final BigDecimal MAX_QUANTITY_EXCLUSIVE = BigDecimal.TEN.pow(QUANTITY_PRECISION - QUANTITY_SCALE);

    private FinancialPrecision() {}

    public static BigDecimal normalizePrice(BigDecimal value) {
        return normalize(value, PRICE_SCALE, MAX_PRICE_EXCLUSIVE, "Preço");
    }

    public static BigDecimal normalizeQuantity(BigDecimal value) {
        return normalize(value, QUANTITY_SCALE, MAX_QUANTITY_EXCLUSIVE, "Quantidade");
    }

    private static BigDecimal normalize(BigDecimal value, int scale, BigDecimal maximumExclusive, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " não pode ser nulo");
        }
        BigDecimal rounded = value.setScale(scale, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(maximumExclusive) >= 0) {
            throw new IllegalArgumentException(label + " excede o limite de precisão permitido");
        }
        return rounded;
    }
}
