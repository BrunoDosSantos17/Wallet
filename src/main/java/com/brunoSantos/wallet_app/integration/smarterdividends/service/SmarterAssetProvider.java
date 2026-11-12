package com.brunoSantos.wallet_app.integration.smarterdividends.service;

import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.provider.AssetTickerResult;
import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import com.brunoSantos.wallet_app.shared.exception.TicketNotFoundException;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Locale;

@Component
public class SmarterAssetProvider implements AssetProvider {
    private final SmarterDividendsClient client;
    public SmarterAssetProvider(SmarterDividendsClient client) { this.client = client; }

    @Override
    public AssetTickerResult validate(String ticker) {
        if (ticker == null || ticker.isBlank()) throw new TicketNotFoundException(404, "Ticker não pode ser vazio");
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        var profile = client.profile(normalized);
        if (profile == null || profile.symbol() == null || !normalized.equalsIgnoreCase(profile.symbol())
                || profile.name() == null || profile.name().isBlank() || !"stock".equalsIgnoreCase(profile.type()))
            throw new TicketNotFoundException(404, "Ticker americano não localizado: " + normalized);
        // A US price is not a BRL quote. Do not persist it as portfolio market value.
        return new AssetTickerResult(normalized, normalized, false, profile.name(), profile.name(),
                "stock_abroad", "stock", null, "USD", null, null, true, null);
    }

    @Override
    public BigDecimal getPrice(String ticker) {
        throw new UnsupportedOperationException("Cotação de stocks em reais ainda não disponível");
    }
}
