package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ImportIdentityTest {
    @Test
    void normalizes_decimal_representation_and_ticker() {
        String first = ImportIdentity.of(1L, " petr4 ", TransactionType.BUY, LocalDate.of(2024, 1, 1),
                new BigDecimal("1.000000000000"), new BigDecimal("10.00000000"));
        assertThat(first).isEqualTo(ImportIdentity.of(1L, "PETR4", TransactionType.BUY,
                LocalDate.of(2024, 1, 1), BigDecimal.ONE, BigDecimal.TEN));
        assertThat(first).isNotEqualTo(ImportIdentity.of(1L, "VALE3", TransactionType.BUY,
                LocalDate.of(2024, 1, 1), BigDecimal.ONE, BigDecimal.TEN));
    }
}
