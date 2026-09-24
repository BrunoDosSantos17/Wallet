package com.brunoSantos.wallet_app.integration.brapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrapiTickerResult(
        @JsonProperty("requestedSymbol") String requestedSymbol,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("changed") Boolean changed,
        @JsonProperty("name") String name,
        @JsonProperty("longName") String longName,
        @JsonProperty("assetType") String assetType,
        @JsonProperty("subType") String subType,
        @JsonProperty("exchange") String exchange,
        @JsonProperty("currency") String currency,
        @JsonProperty("sector") String sector,
        @JsonProperty("subsector") String subsector,
        @JsonProperty("isActive") Boolean isActive
) {}
