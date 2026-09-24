package com.brunoSantos.wallet_app.imports.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrapiTickerResponse {

    @JsonProperty("results")
    private List<TickerResult> results;

    @JsonProperty("requestedAt")
    private String requestedAt;

    @JsonProperty("took")
    private Integer took;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TickerResult {

        @JsonProperty("requestedSymbol")
        private String requestedSymbol;

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("changed")
        private Boolean changed;

        @JsonProperty("name")
        private String name;

        @JsonProperty("longName")
        private String longName;

        @JsonProperty("assetType")
        private String assetType;

        @JsonProperty("subType")
        private String subType;

        @JsonProperty("exchange")
        private String exchange;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("sector")
        private String sector;

        @JsonProperty("subsector")
        private String subsector;

        @JsonProperty("isActive")
        private Boolean isActive;
    }
}
