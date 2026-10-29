package com.brunoSantos.wallet_app.integration.awesomeapi.service;

import com.brunoSantos.wallet_app.integration.awesomeapi.client.AwesomeApiClient;
import com.brunoSantos.wallet_app.integration.awesomeapi.dto.AwesomeApiQuote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwesomeApiServiceTest {

    @Mock
    private AwesomeApiClient awesomeApiClient;

    @InjectMocks
    private AwesomeApiService awesomeApiService;

    @Test
    void should_return_latest_quote_for_a_single_pair() {
        var quote = new AwesomeApiQuote("USD", "BRL", "Dólar Americano/Real Brasileiro",
                new BigDecimal("5.73"), new BigDecimal("5.72"), new BigDecimal("0.01"),
                new BigDecimal("0.17"), new BigDecimal("5.72"), new BigDecimal("5.73"),
                "1618315045", "2021-04-13 08:57:27");
        when(awesomeApiClient.getLatestQuote("USD-BRL")).thenReturn(Map.of("USDBRL", quote));

        var result = awesomeApiService.getLatestQuote("usd-brl");

        assertThat(result).isEqualTo(quote);
        verify(awesomeApiClient).getLatestQuote("USD-BRL");
    }

    @Test
    void should_round_bid_and_ask_up_to_two_decimal_places() {
        var quote = new AwesomeApiQuote("USD", "BRL", "Dólar Americano/Real Brasileiro",
                null, null, null, null, new BigDecimal("5.721"), new BigDecimal("5.730"),
                "1618315045", "2021-04-13 08:57:27");
        when(awesomeApiClient.getLatestQuote("USD-BRL")).thenReturn(Map.of("USDBRL", quote));

        var result = awesomeApiService.getLatestQuote("USD-BRL");

        assertThat(result.bid()).isEqualByComparingTo("5.73");
        assertThat(result.bid().scale()).isEqualTo(2);
        assertThat(result.ask()).isEqualByComparingTo("5.73");
        assertThat(result.ask().scale()).isEqualTo(2);
    }

    @Test
    void should_reject_malformed_currency_pair() {
        assertThatThrownBy(() -> awesomeApiService.getLatestQuote("USDBRL"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void should_return_not_found_when_provider_has_no_quote() {
        when(awesomeApiClient.getLatestQuote("XXX-YYY")).thenReturn(Map.of());

        assertThatThrownBy(() -> awesomeApiService.getLatestQuote("XXX-YYY"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }
}
