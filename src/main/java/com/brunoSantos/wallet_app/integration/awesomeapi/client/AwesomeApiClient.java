package com.brunoSantos.wallet_app.integration.awesomeapi.client;

import com.brunoSantos.wallet_app.integration.awesomeapi.dto.AwesomeApiQuote;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange
public interface AwesomeApiClient {

    @GetExchange("/json/last/{currencyPair}")
    Map<String, AwesomeApiQuote> getLatestQuote(@PathVariable("currencyPair") String currencyPair);
}
