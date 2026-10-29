package com.brunoSantos.wallet_app.integration.awesomeapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AwesomeApiQuote(
        String code,
        @JsonProperty("codein") String codeIn,
        String name,
        BigDecimal high,
        BigDecimal low,
        @JsonProperty("varBid") BigDecimal variation,
        @JsonProperty("pctChange") BigDecimal percentageChange,
        BigDecimal bid,
        BigDecimal ask,
        String timestamp,
        @JsonProperty("create_date") String createDate
) {
    public AwesomeApiQuote {
        if (bid != null) {
            bid = bid.setScale(2, RoundingMode.CEILING);
        }
        if (ask != null) {
            ask = ask.setScale(2, RoundingMode.CEILING);
        }
    }
}
