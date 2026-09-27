package com.brunoSantos.wallet_app.integration.brapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BrapiTickerResponse(
        @JsonProperty("results") List<BrapiTickerResult> results,
        @JsonProperty("requestedAt") String requestedAt,
        @JsonProperty("took") Integer took
) {}
