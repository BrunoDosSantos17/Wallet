package com.brunoSantos.wallet_app.asset.provider;

import java.math.BigDecimal;

public interface AssetProvider {

    BigDecimal getPrice(String ticker);

    AssetTickerResult validate(String ticker);
}
