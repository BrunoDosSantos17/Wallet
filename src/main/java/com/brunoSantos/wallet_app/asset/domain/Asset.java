package com.brunoSantos.wallet_app.asset.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    private String ticker;

    private String name;

    @Enumerated(EnumType.STRING)
    private AssetType type;

    @Column(precision = FinancialPrecision.PRICE_PRECISION, scale = FinancialPrecision.PRICE_SCALE)
    private BigDecimal currentPrice;

    private LocalDateTime lastUpdate;


    public void updatePrice(BigDecimal price, LocalDateTime lastUpdate) {
        this.currentPrice = FinancialPrecision.normalizePrice(price);
        this.lastUpdate = lastUpdate;
    }

}
