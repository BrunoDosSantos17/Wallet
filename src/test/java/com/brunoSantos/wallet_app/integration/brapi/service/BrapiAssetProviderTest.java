package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiQuote;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiResponse;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiStock;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiTickerResponse;
import com.brunoSantos.wallet_app.integration.brapi.dto.BrapiTickerResult;
import com.brunoSantos.wallet_app.shared.exception.TicketNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrapiAssetProviderTest {

    @Mock
    private BrapiClient brapiClient;

    @InjectMocks
    private BrapiAssetProvider provider;

    private BrapiTickerResult result(String symbol, Boolean active) {
        return new BrapiTickerResult(symbol, symbol, false, "Empresa", "Empresa S.A.",
                "stock", "stock", "B3", "BRL", null, null, active,
                new BrapiQuote(new BigDecimal("12.34"), null, null, null));
    }

    @Test
    void should_accept_matching_active_ticker() {
        ReflectionTestUtils.setField(provider, "token", "test-token");
        when(brapiClient.getTickerInfo("PETR4", 1, "test-token"))
                .thenReturn(new BrapiTickerResponse(List.of(result("PETR4", true)), null, null));

        var validated = provider.validate("petr4");

        assertThat(validated.symbol()).isEqualTo("PETR4");
        assertThat(validated.isActive()).isTrue();
        assertThat(validated.lastPrice()).isEqualByComparingTo("12.34");
    }

    @Test
    void should_reject_empty_results() {
        ReflectionTestUtils.setField(provider, "token", "test-token");
        when(brapiClient.getTickerInfo("UNKNOWN", 1, "test-token"))
                .thenReturn(new BrapiTickerResponse(List.of(), null, null));

        assertThatThrownBy(() -> provider.validate("UNKNOWN"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void should_reject_mismatched_or_inactive_symbols_including_null_status() {
        ReflectionTestUtils.setField(provider, "token", "test-token");
        when(brapiClient.getTickerInfo("PETR4", 1, "test-token"))
                .thenReturn(new BrapiTickerResponse(List.of(result("PETR3", true)), null, null));
        assertThatThrownBy(() -> provider.validate("PETR4"))
                .isInstanceOf(TicketNotFoundException.class);

        when(brapiClient.getTickerInfo("VALE3", 1, "test-token"))
                .thenReturn(new BrapiTickerResponse(List.of(result("VALE3", null)), null, null));
        assertThatThrownBy(() -> provider.validate("VALE3"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void should_reject_missing_or_non_positive_quote() {
        ReflectionTestUtils.setField(provider, "token", "test-token");
        when(brapiClient.getQuote("PETR4", "test-token"))
                .thenReturn(new BrapiResponse(List.of(new BrapiStock("PETR4", BigDecimal.ZERO))));

        assertThatThrownBy(() -> provider.getPrice("PETR4"))
                .isInstanceOf(TicketNotFoundException.class);
    }
}
