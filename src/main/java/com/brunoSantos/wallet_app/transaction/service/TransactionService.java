package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.domain.AssetType;
import com.brunoSantos.wallet_app.asset.provider.AssetProvider;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.integration.smarterdividends.service.SmarterAssetProvider;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.transaction.exception.TransactionException;
import com.brunoSantos.wallet_app.transaction.exception.DuplicateImportException;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final AssetPositionRepository positionRepository;
    private final AssetProvider assetProvider;
    private final SmarterAssetProvider smarterAssetProvider;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    @Transactional
    public Transaction create(CreateTransactionRequest request) {
        validateRequest(request);
        String ticker = request.ticker().trim().toUpperCase(Locale.ROOT);
        var provider = "stock_abroad".equalsIgnoreCase(request.assetType()) ? smarterAssetProvider : assetProvider;
        return createValidated(request, ticker, provider.validate(ticker), false);
    }

    @Transactional
    public Transaction create(CreateTransactionRequest request,
                              com.brunoSantos.wallet_app.asset.provider.AssetTickerResult tickerInfo) {
        validateRequest(request);
        String ticker = request.ticker().trim().toUpperCase(Locale.ROOT);
        return createValidated(request, ticker, tickerInfo, false);
    }

    @Transactional
    public Transaction createImported(CreateTransactionRequest request,
                                      com.brunoSantos.wallet_app.asset.provider.AssetTickerResult tickerInfo) {
        validateRequest(request);
        return createValidated(request, request.ticker().trim().toUpperCase(Locale.ROOT), tickerInfo, true);
    }

    private Transaction createValidated(CreateTransactionRequest request, String ticker,
                                        com.brunoSantos.wallet_app.asset.provider.AssetTickerResult tickerInfo,
                                        boolean imported) {
        if (tickerInfo == null || tickerInfo.symbol() == null
                || !tickerInfo.symbol().trim().equalsIgnoreCase(ticker)
                || !Boolean.TRUE.equals(tickerInfo.isActive())) {
            throw new TransactionException("Ticker não localizado ou inativo: " + ticker);
        }
        BigDecimal quantity;
        BigDecimal transactionPrice;
        try {
            quantity = FinancialPrecision.normalizeQuantity(request.quantity());
            transactionPrice = FinancialPrecision.normalizePrice(request.price());
        } catch (IllegalArgumentException ex) {
            throw new TransactionException(ex.getMessage());
        }
        if (quantity.signum() <= 0 || transactionPrice.signum() <= 0) {
            throw new TransactionException("Quantidade e preço devem permanecer maiores que zero após o arredondamento");
        }
        var wallet = walletRepository.findByIdForUpdate(request.walletId())
                .orElseThrow(WalletNotFoundException::new);
        LocalDate transactionDate = parseDate(request.date());
        String importIdentity = imported ? ImportIdentity.of(wallet.getId(), ticker, request.type(),
                transactionDate, quantity, transactionPrice) : null;
        if (imported) {
            var existing = transactionRepository.findByWalletIdAndImportIdentity(wallet.getId(), importIdentity);
            if (existing.isPresent()) {
                Transaction previous = existing.get();
                boolean sameOperation = previous.getAsset().getTicker().equals(ticker)
                        && previous.getType() == request.type()
                        && transactionDate.equals(previous.getDataTransaction())
                        && previous.getQuantity().compareTo(quantity) == 0
                        && previous.getPrice().compareTo(transactionPrice) == 0;
                if (!sameOperation) {
                    throw new TransactionException("Colisão de identificador de importação; análise manual necessária");
                }
                throw new DuplicateImportException();
            }
        }
        LocalDate latestDate = transactionRepository.findLatestDate(wallet.getId(), ticker);
        if (latestDate != null && transactionDate.isBefore(latestDate)) {
            throw new TransactionException("Operação retroativa não permitida; última data: " + latestDate);
        }

        var price = tickerInfo.lastPrice();
        if (price != null && price.signum() <= 0) {
            price = null;
        }
        if (price != null) {
            try {
                price = FinancialPrecision.normalizePrice(price);
                if (price.signum() <= 0) price = null;
            } catch (IllegalArgumentException ex) {
                price = null;
            }
        }
        var validatedPrice = price;
        var asset = assetRepository.findByTicker(ticker)
                .orElseGet(() -> assetRepository.save(Asset.builder()
                        .name(tickerInfo.longName() != null ? tickerInfo.longName() : tickerInfo.name())
                        .ticker(ticker)
                        .type(mapAssetType(tickerInfo.assetType()))
                        .currentPrice(validatedPrice != null ? validatedPrice : BigDecimal.ZERO)
                        .lastUpdate(validatedPrice != null ? LocalDateTime.now() : null)
                        .build()));

        if (asset.getType() != null && mapAssetType(tickerInfo.assetType()) != null
                && asset.getType() != mapAssetType(tickerInfo.assetType()))
            throw new TransactionException("Ticker já cadastrado com outro tipo de ativo");

        if (validatedPrice != null) {
            asset.updatePrice(validatedPrice, LocalDateTime.now());
        }

        var position = positionRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseGet(() -> createEmptyPosition(wallet, asset));

        if (request.type() == TransactionType.BUY) {
            position.buy(quantity, transactionPrice);
        } else {
            position.sell(quantity);
        }

        positionRepository.save(position);

        return transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .asset(asset)
                .type(request.type())
                .quantity(quantity)
                .price(transactionPrice)
                .date(LocalDate.now())
                .dataTransaction(transactionDate)
                .importIdentity(importIdentity)
                .build());
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new TransactionException("Data inválida. Use o formato dd/MM/yyyy");
        }
    }

    private void validateRequest(CreateTransactionRequest request) {
        if (request == null || request.walletId() == null || request.walletId() <= 0) {
            throw new TransactionException("Identificador da carteira deve ser maior que zero");
        }
        if (request.ticker() == null || request.ticker().isBlank()) {
            throw new TransactionException("Ticker não pode ser vazio");
        }
        if (request.type() == null) {
            throw new TransactionException("Tipo de transação é obrigatório");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new TransactionException("Quantidade deve ser maior que zero");
        }
        if (request.price() == null || request.price().signum() <= 0) {
            throw new TransactionException("Preço deve ser maior que zero");
        }
    }

    private AssetPosition createEmptyPosition(Wallet wallet, Asset asset) {
        return AssetPosition
                .builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .build();
    }

    private AssetType mapAssetType(String assetType) {
        if (assetType == null) {
            return null;
        }
        return switch (assetType.trim().toLowerCase(Locale.ROOT)) {
            case "stock" -> AssetType.STOCK;
            case "stock_abroad" -> AssetType.STOCK_ABROAD;
            case "fund", "fii" -> AssetType.FII;
            case "etf" -> AssetType.ETF;
            case "bdr" -> AssetType.BDR;
            case "fixed_income" -> AssetType.FIXED_INCOME;
            default -> null;
        };
    }
}
