package com.brunoSantos.position.domain;

import com.brunoSantos.asset.domain.Asset;
import com.brunoSantos.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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

    public void buy(BigDecimal buyQuantity, BigDecimal buyPrice) {

        BigDecimal totalInvested = this.quantity.multiply(this.averagePrice)
                .add(buyQuantity.multiply(buyPrice));

        BigDecimal newQuantity = this.quantity.add(buyQuantity);

        BigDecimal newAveragePrice = totalInvested
                .divide(newQuantity, 6, RoundingMode.HALF_UP);

        this.quantity = newQuantity;
        this.averagePrice = newAveragePrice;
    }

    public void sell(BigDecimal sellQuantity) {

        if (this.quantity.compareTo(sellQuantity) < 0) {
            throw new IllegalArgumentException("Insufficient quantity for sell");
        }

        this.quantity = this.quantity.subtract(sellQuantity);

        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            this.averagePrice = BigDecimal.ZERO;
        }
    }

    public BigDecimal getInvestedValue() {
        return quantity.multiply(averagePrice);
    }

    public BigDecimal getCurrentValue() {
        if (asset.getCurrentPrice() == null) return BigDecimal.ZERO;
        return quantity.multiply(asset.getCurrentPrice());
    }

    public BigDecimal getProfit() {
        return getCurrentValue().subtract(getInvestedValue());
    }
}
