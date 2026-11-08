package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

public final class ImportIdentity {
    private ImportIdentity() {}

    public static String of(Long walletId, String ticker, TransactionType type, LocalDate date,
                            BigDecimal quantity, BigDecimal price) {
        String input = walletId + "|" + ticker.trim().toUpperCase(Locale.ROOT) + "|" + type.name()
                + "|" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "|"
                + quantity.stripTrailingZeros().toPlainString() + "|" + price.stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
