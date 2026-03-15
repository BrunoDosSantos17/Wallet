package com.brunoSantos.wallet_app.integration.brapi.client;

import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/quote")
public interface BrapiClient {

    @GetExchange("/{ticker}")
    BrapiResponse getQuote(@PathVariable String ticker, @RequestParam String token);
}