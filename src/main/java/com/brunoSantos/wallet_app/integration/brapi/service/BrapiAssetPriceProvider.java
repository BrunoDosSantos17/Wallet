package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.asset.provider.AssetPriceProvider;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BrapiAssetPriceProvider implements AssetPriceProvider {

    private final BrapiClient brapiClient;

    @Value("${brapi.token}")
    private String token;

    @Override
    public BigDecimal getPrice(String ticker) {
        var response = brapiClient.getQuote(ticker, token); // ← token real
        return response.results()
                .getFirst()
                .regularMarketPrice();
    }
}