package com.brunoSantos.wallet_app.integration.brapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

public final class BrapiDividendResponses {
    private BrapiDividendResponses() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String symbol, String label, String lastDatePrior, String paymentDate,
                        String approvedOn, String isinCode, String relatedTo, String remarks,
                        BigDecimal rawRate) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<Event> cashDividends) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String requestedSymbol, String symbol, Data data) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stocks(List<Result> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fiis(List<Event> dividends) {}
}
