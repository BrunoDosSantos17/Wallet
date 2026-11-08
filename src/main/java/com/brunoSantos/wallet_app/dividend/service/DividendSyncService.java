package com.brunoSantos.wallet_app.dividend.service;

import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.dividend.domain.DividendoRecebido;
import com.brunoSantos.wallet_app.dividend.domain.TipoProvento;
import com.brunoSantos.wallet_app.dividend.repository.DividendoRecebidoRepository;
import com.brunoSantos.wallet_app.dividend.provider.DividendProvider;
import com.brunoSantos.wallet_app.dividend.provider.DividendProvider.Event;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DividendSyncService {
    private final AssetPositionRepository positions;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final DividendoRecebidoRepository dividends;
    private final List<DividendProvider> providers;

    public DividendSyncService(AssetPositionRepository positions, WalletRepository wallets,
                               TransactionRepository transactions, DividendoRecebidoRepository dividends,
                               List<DividendProvider> providers) {
        this.positions = positions;
        this.wallets = wallets;
        this.transactions = transactions;
        this.dividends = dividends;
        this.providers = providers;
    }

    public record SyncResult(int inseridos, int existentes, String aviso) {}

    @Transactional
    public SyncResult sync(Long positionId, LocalDate inicio) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if (inicio == null || inicio.isAfter(hoje)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inicial inválida");
        var position = positions.findById(positionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Posição não encontrada"));
        var walletId = position.getWallet().getId();
        wallets.findByIdForUpdate(walletId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteira não encontrada"));
        String ticker = position.getAsset().getTicker();
        AssetType assetType = position.getAsset().getType();
        var matchingProviders = providers.stream().filter(provider -> provider.supports(assetType)).toList();
        if (matchingProviders.size() != 1) throw invalid("Fonte de proventos não configurada ou ambígua");
        List<Event> events = matchingProviders.getFirst().getEvents(ticker, assetType, inicio, hoje);

        List<Transaction> history = transactions.findByWalletIdAndAssetTickerOrderByDataTransactionAscIdAsc(walletId, ticker);
        if (history.stream().anyMatch(t -> t.getDataTransaction() == null || t.getQuantity() == null || t.getType() == null))
            throw invalid("Histórico de transações incompleto; revisão necessária");
        Set<String> seen = new HashSet<>();
        int inserted = 0, existing = 0;
        for (Event event : events) {
            if (event == null || event.label() == null) throw invalid("Evento sem classificação; revisão necessária");
            TipoProvento type = classify(event.label(), assetType);
            if (type == null) continue;
            LocalDate base = event.dataBase();
            LocalDate payment = event.pagamento();
            if (base == null || payment == null) throw invalid("Data do evento ausente");
            if (payment.isAfter(hoje) || payment.isBefore(inicio)) continue;
            if (event.valorUnitario() == null || event.valorUnitario().signum() < 0 || event.valorUnitario().scale() > 12)
                throw invalid("Valor original por unidade indisponível ou impreciso; revisão necessária");
            BigDecimal unit = FinancialPrecision.normalizeQuantity(event.valorUnitario());
            BigDecimal quantity = BigDecimal.ZERO;
            for (Transaction transaction : history) {
                if (transaction.getDataTransaction().isAfter(base)) break;
                quantity = transaction.getType() == TransactionType.BUY
                        ? quantity.add(transaction.getQuantity()) : quantity.subtract(transaction.getQuantity());
                if (quantity.signum() < 0) throw invalid("Histórico de posição inconsistente; revisão necessária");
            }
            quantity = FinancialPrecision.normalizeQuantity(quantity);
            String identity = hash(type.name() + "|" + base);
            if (!seen.add(identity)) throw invalid("Eventos com mesma data-base e tipo; revisão necessária");
            String signature = hash(payment + "|" + unit.toPlainString() + "|" + Objects.toString(event.detalhes(), ""));
            var previous = dividends.findByAssetPositionIdAndIdentificadorOrigem(positionId, identity);
            if (previous.isPresent()) {
                var old = previous.get();
                if (!event.currency().equals(old.getMoeda()) || old.getTipoProvento() != type || !base.equals(old.getDataBase())
                        || !signature.equals(old.getAssinaturaEvento()) || old.getQuantidadeElegivel() == null
                        || old.getQuantidadeElegivel().compareTo(quantity) != 0)
                    throw invalid("Evento ou quantidade histórica alterado; revisão necessária");
                existing++;
            } else if (quantity.signum() > 0) {
                BigDecimal amount = FinancialPrecision.normalizePrice(unit.multiply(quantity));
                var dividend = new DividendoRecebido(position, amount, type, payment, identity, base, quantity, unit, signature);
                dividend.setMoeda(event.currency());
                dividends.save(dividend);
                inserted++;
            }
        }
        return new SyncResult(inserted, existing, "Valores brutos calculados, não créditos confirmados; histórico pode estar incompleto. Stocks: apenas último pagamento, fonte https://smarterdividends.com.");
    }

    private static TipoProvento classify(String label, AssetType assetType) {
        String value = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
        if (assetType == AssetType.FII) return value.equals("RENDIMENTO") ? TipoProvento.RENDIMENTO_FII : null;
        if (value.equals("DIVIDENDO") || value.equals("DIVIDENDOS")) return TipoProvento.DIVIDENDO;
        if (value.equals("JCP") || value.equals("JUROS SOBRE CAPITAL PROPRIO")) return TipoProvento.JCP;
        return null;
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
