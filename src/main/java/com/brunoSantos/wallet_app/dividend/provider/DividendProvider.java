package com.brunoSantos.wallet_app.dividend.provider;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DividendProvider {
    record Event(String label, LocalDate dataBase, LocalDate pagamento, BigDecimal valorUnitario,
                 String currency, String detalhes) {}
    boolean supports(AssetType type);
    List<Event> getEvents(String ticker, AssetType type, LocalDate inicio, LocalDate fim);
}
