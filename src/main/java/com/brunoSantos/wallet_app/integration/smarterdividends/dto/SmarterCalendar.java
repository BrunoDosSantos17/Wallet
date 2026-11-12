package com.brunoSantos.wallet_app.integration.smarterdividends.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SmarterCalendar(@JsonProperty("ex_date") String exDate, @JsonProperty("last_day_to_buy") String lastDayToBuy,
                              List<Entry> dividends) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String symbol, @JsonProperty("pay_date") String payDate, BigDecimal amount) {}
}
