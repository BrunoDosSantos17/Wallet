package com.brunoSantos.wallet_app.integration.smarterdividends.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmarterDividendProfile(String symbol, String name, String type, String url, Dividend dividend) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dividend(@JsonProperty("last_payment") Payment lastPayment) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(@JsonProperty("ex_date") String exDate, @JsonProperty("pay_date") String payDate, BigDecimal amount) {}
}
