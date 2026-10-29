package com.brunoSantos.wallet_app.integration.awesomeapi.service;

import com.brunoSantos.wallet_app.integration.awesomeapi.client.AwesomeApiClient;
import com.brunoSantos.wallet_app.integration.awesomeapi.dto.AwesomeApiQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AwesomeApiService {

    private static final Pattern CURRENCY_PAIR_PATTERN = Pattern.compile("^[A-Za-z]{3}-[A-Za-z]{3}$");

    private final AwesomeApiClient awesomeApiClient;

    public AwesomeApiQuote getLatestQuote(String currencyPair) {
        if (currencyPair == null || !CURRENCY_PAIR_PATTERN.matcher(currencyPair).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Par inválido. Informe o formato XXX-YYY, por exemplo USD-BRL.");
        }

        var normalizedPair = currencyPair.toUpperCase(Locale.ROOT);
        var quotes = awesomeApiClient.getLatestQuote(normalizedPair);

        if (quotes == null || quotes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Nenhuma cotação encontrada para o par " + normalizedPair);
        }

        return quotes.values().iterator().next();
    }
}
