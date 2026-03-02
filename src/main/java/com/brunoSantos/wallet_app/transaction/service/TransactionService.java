package com.brunoSantos.wallet_app.transaction.service;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.asset.repository.AssetRepository;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import com.brunoSantos.wallet_app.transaction.domain.TransactionType;
import com.brunoSantos.wallet_app.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.wallet_app.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;
    private final AssetPositionRepository positionRepository;

    @Transactional
    public Transaction create(CreateTransactionRequest request) {

        var wallet = walletRepository.findById(request.walletId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        var asset = assetRepository.findByTicker(request.ticker())
                .orElse(assetRepository.save(Asset.builder()
                        .name(request.ticker()).
                        ticker(request.ticker())
                        .currentPrice(BigDecimal.ZERO)
                        .lastUpdate(LocalDateTime.now())
                        .build()));

        var position = positionRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseGet(() -> createEmptyPosition(wallet, asset));

        if (request.type() == TransactionType.BUY) {
            position.buy(request.quantity(), request.price());
        } else {
            position.sell(request.quantity());
        }

        positionRepository.save(position);

        return transactionRepository.save(Transaction.builder()
                .wallet(wallet)
                .asset(asset)
                .type(request.type())
                .quantity(request.quantity())
                .price(request.price())
                .date(LocalDate.now())
                .build());
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


}
