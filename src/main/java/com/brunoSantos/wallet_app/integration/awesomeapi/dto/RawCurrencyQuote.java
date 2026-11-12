package com.brunoSantos.wallet_app.integration.awesomeapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RawCurrencyQuote(String code, @JsonProperty("codein") String codeIn, BigDecimal bid) {}
