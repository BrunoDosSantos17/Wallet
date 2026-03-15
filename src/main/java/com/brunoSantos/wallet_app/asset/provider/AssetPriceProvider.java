package com.brunoSantos.wallet_app.asset.provider;

import java.math.BigDecimal;

public interface AssetPriceProvider {

    BigDecimal getPrice(String ticker);

}
