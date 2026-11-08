package com.brunoSantos.wallet_app.integration.smarterdividends.service;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.dividend.provider.DividendProvider;
import com.brunoSantos.wallet_app.integration.smarterdividends.client.SmarterDividendsClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@Component
public class SmarterDividendProvider implements DividendProvider {
    private final SmarterDividendsClient client;
    public SmarterDividendProvider(SmarterDividendsClient client) { this.client = client; }
    public boolean supports(AssetType type) { return type == AssetType.STOCK_ABROAD; }

    public List<Event> getEvents(String ticker, AssetType type, LocalDate inicio, LocalDate fim) {
        var profile = client.profile(ticker);
        if (profile == null || profile.symbol() == null || !ticker.equalsIgnoreCase(profile.symbol()) || profile.dividend() == null)
            throw conflict("Resposta SmarterDividends incompleta ou ativo divergente");
        var payment = profile.dividend().lastPayment();
        if (payment == null) return List.of();
        try {
            var pay = LocalDate.parse(payment.payDate());
            if (pay.isBefore(inicio) || pay.isAfter(fim)) return List.of();
            var ex = LocalDate.parse(payment.exDate());
            var calendar = client.calendar(ex.toString());
            if (calendar == null || !ex.toString().equals(calendar.exDate()) || calendar.dividends() == null
                    || calendar.lastDayToBuy() == null || payment.amount() == null)
                throw conflict("Calendário SmarterDividends incompleto");
            var matching = calendar.dividends().stream().filter(e -> e != null && ticker.equalsIgnoreCase(e.symbol())
                    && payment.payDate().equals(e.payDate()) && e.amount() != null && e.amount().compareTo(payment.amount()) == 0).toList();
            if (matching.size() != 1) throw conflict("Evento SmarterDividends ambíguo; revisão necessária");
            return List.of(new Event("DIVIDENDO", LocalDate.parse(calendar.lastDayToBuy()), pay, payment.amount(), "USD", ex.toString()));
        } catch (java.time.DateTimeException | NullPointerException ex) {
            throw conflict("Datas SmarterDividends ausentes ou inválidas");
        }
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
