package com.brunoSantos.wallet_app.wallet.service;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.dto.UpdateWalletRequest;
import com.brunoSantos.wallet_app.wallet.dto.WalletBasicInfoResponse;
import com.brunoSantos.wallet_app.wallet.exception.WalletExistsException;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.position.repository.AssetPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final AssetPositionRepository assetPositionRepository;

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Wallet createWallet(String name) {

        if(walletRepository.findByName(name).isPresent()) {
            throw new WalletExistsException();
        };

        return walletRepository.save(Wallet.builder().name(name).build());
    }

    public Wallet findById(Long id) {
        return walletRepository.findById(id)
                .orElseThrow(WalletNotFoundException::new);
    }

    public List<Wallet> findAll() {
        return walletRepository.findAll();
    }

    public WalletBasicInfoResponse infoBasica(Long walletId) {
        Wallet wallet = findById(walletId);
        List<AssetPosition> positions = assetPositionRepository.findAllByWallet(wallet).stream()
                .filter(position -> position.getQuantity() != null
                        && position.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalPatrimony = positions.stream()
                .map(this::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<AssetPosition>> positionsByType = positions.stream()
                .collect(Collectors.groupingBy(position -> position.getAsset().getType() == null
                        ? "OUTROS"
                        : position.getAsset().getType().name()));

        List<WalletBasicInfoResponse.AssetDistribution> distribution = positionsByType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toDistribution(entry.getKey(), entry.getValue(), totalPatrimony))
                .toList();

        return new WalletBasicInfoResponse(
                totalPatrimony.setScale(2, RoundingMode.HALF_UP),
                ZERO_MONEY,
                positions.size(),
                distribution
        );
    }

    private WalletBasicInfoResponse.AssetDistribution toDistribution(
            String assetType, List<AssetPosition> positions, BigDecimal totalPatrimony) {
        BigDecimal value = positions.stream()
                .map(this::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal quantity = positions.stream()
                .map(AssetPosition::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal percentage = totalPatrimony.compareTo(BigDecimal.ZERO) == 0
                ? ZERO_MONEY
                : value.multiply(HUNDRED).divide(totalPatrimony, 2, RoundingMode.HALF_UP);

        return new WalletBasicInfoResponse.AssetDistribution(
                assetType,
                formatPercentage(percentage),
                value.stripTrailingZeros().toPlainString(),
                quantity.intValue()
        );
    }

    private String formatPercentage(BigDecimal percentage) {
        DecimalFormat formatter = new DecimalFormat("0.00%", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        return formatter.format(percentage.divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal marketValue(AssetPosition position) {
        BigDecimal currentPrice = position.getAsset().getCurrentPrice();
        if (currentPrice == null || position.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return currentPrice.multiply(position.getQuantity());
    }

    public void delete(Long id) {
        walletRepository.delete(findById(id));
    }

    public Wallet update(UpdateWalletRequest updateWallet) {
        var wallet =  walletRepository.findById(updateWallet.id())
                .orElseThrow(WalletNotFoundException::new);

        wallet.setName(updateWallet.name());

        return walletRepository.save(wallet);

    }
}
