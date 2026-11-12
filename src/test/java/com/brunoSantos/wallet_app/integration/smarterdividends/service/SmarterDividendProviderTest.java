package com.brunoSantos.wallet_app.integration.smarterdividends.service;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterCalendar;
import com.brunoSantos.wallet_app.integration.smarterdividends.dto.SmarterDividendProfile;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmarterDividendProviderTest {
    @Test
    void maps_last_payment_with_last_day_to_buy_and_dollars() {
        var client = mock(SmarterDividendsClient.class);
        when(client.profile("KO")).thenReturn(new SmarterDividendProfile("KO", "Coca Cola", "stock", "https://smarterdividends.com/stocks/KO",
                new SmarterDividendProfile.Dividend(new SmarterDividendProfile.Payment("2026-09-15", "2026-10-01", new BigDecimal("0.53")))));
        when(client.calendar("2026-09-15")).thenReturn(new SmarterCalendar("2026-09-15", "2026-09-14", List.of(
                new SmarterCalendar.Entry("KO", "2026-10-01", new BigDecimal("0.53")))));
        var events = new SmarterDividendProvider(client).getEvents("KO", AssetType.STOCK_ABROAD,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.dataBase()).isEqualTo(LocalDate.parse("2026-09-14"));
            assertThat(event.currency()).isEqualTo("USD");
            assertThat(event.valorUnitario()).isEqualByComparingTo("0.53");
        });
    }

    @Test
    void rejects_ambiguous_calendar() {
        var client = mock(SmarterDividendsClient.class);
        var payment = new SmarterDividendProfile.Payment("2026-09-15", "2026-10-01", BigDecimal.ONE);
        var entry = new SmarterCalendar.Entry("KO", "2026-10-01", BigDecimal.ONE);
        when(client.profile("KO")).thenReturn(new SmarterDividendProfile("KO", "Coca Cola", "stock", "url", new SmarterDividendProfile.Dividend(payment)));
        when(client.calendar("2026-09-15")).thenReturn(new SmarterCalendar("2026-09-15", "2026-09-14", List.of(entry, entry)));
        assertThatThrownBy(() -> new SmarterDividendProvider(client).getEvents("KO", AssetType.STOCK_ABROAD,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))).hasMessageContaining("ambíguo");
    }
}
