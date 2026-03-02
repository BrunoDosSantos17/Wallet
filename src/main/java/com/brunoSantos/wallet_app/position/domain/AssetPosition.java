package com.brunoSantos.wallet_app.position.domain;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssetPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Wallet wallet;

    @ManyToOne(optional = false)
    private Asset asset;

    private BigDecimal quantity;

    private BigDecimal averagePrice;

    // ===============================
    // Regras de domínio aqui dentro
    // ===============================

    public void buy(BigDecimal quantityToBuy, BigDecimal price) {
        validatePositive(quantityToBuy);
        validatePositive(price);

        BigDecimal totalCurrentValue = this.averagePrice.multiply(this.quantity);
        BigDecimal totalNewValue = price.multiply(quantityToBuy);

        BigDecimal newQuantity = this.quantity.add(quantityToBuy);

        BigDecimal newAveragePrice = totalCurrentValue
                .add(totalNewValue)
                .divide(newQuantity, 8, RoundingMode.HALF_UP);

        this.quantity = newQuantity;
        this.averagePrice = newAveragePrice;
    }

    public void sell(BigDecimal quantityToSell) {
        validatePositive(quantityToSell);

        if (quantityToSell.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("Cannot sell more than owned quantity");
        }

        this.quantity = this.quantity.subtract(quantityToSell);

        // Se zerar posição, zera preço médio
        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePrice = BigDecimal.ZERO;
        }
    }

    private void validatePositive(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Value must be greater than zero");
        }
    }

}
