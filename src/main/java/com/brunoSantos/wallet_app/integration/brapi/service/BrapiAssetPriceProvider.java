package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.asset.provider.AssetPriceProvider;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class BrapiAssetPriceProvider implements AssetPriceProvider {

    private final BrapiClient brapiClient;

    @Override
    public BigDecimal getPrice(String ticker) {

        var response = brapiClient.getQuote(ticker, "");

        return response.results()
                .getFirst()
                .regularMarketPrice();
    }
}
