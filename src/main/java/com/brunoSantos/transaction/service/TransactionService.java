package com.brunoSantos.transaction.service;

import com.brunoSantos.asset.domain.Asset;
import com.brunoSantos.asset.repository.AssetRepository;
import com.brunoSantos.position.domain.AssetPosition;
import com.brunoSantos.position.repository.AssetPositionRepository;
import com.brunoSantos.transaction.domain.Transaction;
import com.brunoSantos.transaction.domain.TransactionType;
import com.brunoSantos.transaction.dto.CreateTransactionRequest;
import com.brunoSantos.transaction.repository.TransactionRepository;
import com.brunoSantos.wallet.domain.Wallet;
import com.brunoSantos.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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
                .orElseThrow(() -> new RuntimeException("Asset not found"));

        var position = positionRepository
                .findByWalletAndAsset(wallet, asset)
                .orElseGet(() -> createEmptyPosition(wallet, asset));

        if (request.type() == TransactionType.BUY) {
            handleBuy(position, request);
        } else {
            handleSell(position, request);
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

    private void handleBuy(AssetPosition position, CreateTransactionRequest request) {

        var totalInvested = position.getQuantity()
                .multiply(position.getAveragePrice())
                .add(request.quantity().multiply(request.price()));

        var newQuantity = position.getQuantity().add(request.quantity());

        var newAvgPrice = totalInvested.divide(newQuantity, 6, RoundingMode.HALF_UP);

        position.setQuantity(newQuantity);
        position.setAveragePrice(newAvgPrice);
    }

    private void handleSell(AssetPosition position, CreateTransactionRequest request) {

        if (position.getQuantity().compareTo(request.quantity()) < 0) {
            throw new RuntimeException("Insufficient quantity for sell");
        }

        BigDecimal newQuantity = position.getQuantity().subtract(request.quantity());

        position.setQuantity(newQuantity);

        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            position.setAveragePrice(BigDecimal.ZERO);
        }
    }

    private AssetPosition createEmptyPosition(Wallet wallet, Asset asset) {
        return AssetPosition
                .builder()
                .wallet(wallet)
                .asset(asset)
                .quantity(BigDecimal.ZERO)
                .price(BigDecimal.ZERO)
                .build();
    }


}
