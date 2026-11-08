package com.brunoSantos.wallet_app.integration.brapi.service;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.dividend.provider.DividendProvider;
import com.brunoSantos.wallet_app.integration.brapi.client.BrapiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
public class BrapiDividendProvider implements DividendProvider {
    private final BrapiClient client;
    private final String token;
    public BrapiDividendProvider(BrapiClient client, @Value("${brapi.token}") String token) { this.client = client; this.token = token; }
    public boolean supports(AssetType type) { return type == AssetType.STOCK || type == AssetType.FII; }
    public List<Event> getEvents(String ticker, AssetType type, LocalDate inicio, LocalDate fim) {
        List<com.brunoSantos.wallet_app.integration.brapi.dto.BrapiDividendResponses.Event> events;
        if (type == AssetType.FII) {
            var response = client.getFiiDividends(ticker, inicio.toString(), fim.toString(), "Bearer " + token);
            if (response == null || response.dividends() == null || response.dividends().stream().anyMatch(e -> e == null || e.symbol() == null || !ticker.equalsIgnoreCase(e.symbol())))
                throw invalid("Resposta de proventos incompleta ou ativo divergente");
            events = response.dividends();
        } else {
            var response = client.getStockDividends(ticker, inicio.toString(), fim.toString(), "true", "Bearer " + token);
            if (response == null || response.results() == null || response.results().size() != 1 || response.results().getFirst() == null
                    || !ticker.equalsIgnoreCase(response.results().getFirst().requestedSymbol())
                    || !ticker.equalsIgnoreCase(response.results().getFirst().symbol()) || response.results().getFirst().data() == null
                    || response.results().getFirst().data().cashDividends() == null) throw invalid("Resposta de proventos incompleta ou ativo divergente");
            events = response.results().getFirst().data().cashDividends();
        }
        return events.stream().filter(e -> {
            if (e == null || e.label() == null) throw invalid("Evento sem classificação");
            String label = java.text.Normalizer.normalize(e.label(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toUpperCase(java.util.Locale.ROOT);
            return type == AssetType.FII ? label.equals("RENDIMENTO")
                    : label.equals("DIVIDENDO") || label.equals("DIVIDENDOS") || label.equals("JCP")
                      || label.equals("JUROS SOBRE CAPITAL PROPRIO");
        }).map(e -> {
            if (e == null || e.label() == null) throw invalid("Evento sem classificação");
            String details = Objects.toString(e.isinCode(), "") + "|" + Objects.toString(e.relatedTo(), "") + "|"
                    + Objects.toString(e.approvedOn(), "") + "|" + Objects.toString(e.remarks(), "");
            return new Event(e.label(), parseDate(e.lastDatePrior()), parseDate(e.paymentDate()), e.rawRate(), "BRL", details);
        }).toList();
    }
    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (RuntimeException ex) { throw invalid("Data do evento ausente ou inválida"); }
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
