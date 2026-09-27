package com.brunoSantos.wallet_app.integration.brapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrapiQuote(
        @JsonProperty("lastPrice") BigDecimal lastPrice,
        @JsonProperty("changePercent") BigDecimal changePercent,
        @JsonProperty("volume") Long volume,
        @JsonProperty("marketCap") BigDecimal marketCap
) {}
