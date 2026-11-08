package com.brunoSantos.wallet_app.position.domain;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.position.exception.InsufficientBalanceException;
import com.brunoSantos.wallet_app.position.exception.InvalidValueException;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "asset_position", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asset_position_wallet_asset", columnNames = {"wallet_id", "asset_ticker"})
})
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssetPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(optional = false)
    private Wallet wallet;

    @ManyToOne(optional = false)
    private Asset asset;

    @Column(precision = FinancialPrecision.QUANTITY_PRECISION, scale = FinancialPrecision.QUANTITY_SCALE)
    private BigDecimal quantity;

    @Column(precision = FinancialPrecision.PRICE_PRECISION, scale = FinancialPrecision.AVERAGE_PRICE_SCALE)
    private BigDecimal averagePrice;

    // ===============================
    // Regras de domínio aqui dentro
    // ===============================

    public void buy(BigDecimal quantityToBuy, BigDecimal price) {
        validatePositive(quantityToBuy);
        validatePositive(price);

        try {
            quantityToBuy = FinancialPrecision.normalizeQuantity(quantityToBuy);
            price = FinancialPrecision.normalizePrice(price);
        } catch (IllegalArgumentException ex) {
            throw new InvalidValueException(ex.getMessage());
        }
        validatePositive(quantityToBuy);
        validatePositive(price);

        BigDecimal totalCurrentValue = this.averagePrice.multiply(this.quantity);
        BigDecimal totalNewValue = price.multiply(quantityToBuy);

        BigDecimal newQuantity = this.quantity.add(quantityToBuy);
        try {
            newQuantity = FinancialPrecision.normalizeQuantity(newQuantity);
        } catch (IllegalArgumentException ex) {
            throw new InvalidValueException(ex.getMessage());
        }

        BigDecimal newAveragePrice = totalCurrentValue
                .add(totalNewValue)
                .divide(newQuantity, FinancialPrecision.AVERAGE_PRICE_SCALE, RoundingMode.HALF_UP);

        this.quantity = newQuantity;
        this.averagePrice = newAveragePrice;
    }

    public void sell(BigDecimal quantityToSell) {
        validatePositive(quantityToSell);
        try {
            quantityToSell = FinancialPrecision.normalizeQuantity(quantityToSell);
        } catch (IllegalArgumentException ex) {
            throw new InvalidValueException(ex.getMessage());
        }
        validatePositive(quantityToSell);

        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new InsufficientBalanceException("Você não possui posição neste ativo para vender");
        }

        if (quantityToSell.compareTo(this.quantity) > 0) {
            throw new InsufficientBalanceException("Saldo insuficiente. Você possui " + this.quantity + " e tentou vender " + quantityToSell);
        }

        this.quantity = this.quantity.subtract(quantityToSell);

        // Se zerar posição, zera preço médio
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePrice = BigDecimal.ZERO;
        }
    }

    private void validatePositive(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidValueException("Valor deve ser maior que zero");
        }
    }

}
