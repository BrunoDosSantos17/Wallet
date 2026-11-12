package com.brunoSantos.wallet_app.integration.brapi.client;

import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiResponse;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiTickerResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiDividendResponses;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "/")
public interface BrapiClient {

    @GetExchange("quote/{ticker}")
    BrapiResponse getQuote(@PathVariable String ticker, @RequestParam String token);

    @GetExchange("v2/tickers")
    BrapiTickerResponse getTickerInfo(@RequestParam String search, @RequestParam(defaultValue = "1") int limit, @RequestParam String token);

    @GetExchange("v2/stocks/dividends")
    BrapiDividendResponses.Stocks getStockDividends(@RequestParam String symbols, @RequestParam String startDate,
            @RequestParam String endDate, @RequestParam String includeRaw, @RequestHeader("Authorization") String authorization);

    @GetExchange("v2/fii/dividends")
    BrapiDividendResponses.Fiis getFiiDividends(@RequestParam String symbols, @RequestParam String startDate,
            @RequestParam String endDate, @RequestHeader("Authorization") String authorization);
}
