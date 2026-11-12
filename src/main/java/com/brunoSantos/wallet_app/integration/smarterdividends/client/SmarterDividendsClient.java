package com.brunoSantos.wallet_app.integration.smarterdividends.client;

import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterDividendProfile;
import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterCalendar;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1")
public interface SmarterDividendsClient {
    @GetExchange("/stocks/{ticker}")
    SmarterDividendProfile profile(@PathVariable("ticker") String ticker);
    @GetExchange("/ex-dividend/{date}")
    SmarterCalendar calendar(@PathVariable("date") String date);
}
