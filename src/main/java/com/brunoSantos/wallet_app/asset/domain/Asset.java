package com.brunoSantos.wallet_app.asset.domain;

import jakarta.persistence.*;
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

    private BigDecimal currentPrice;

    private LocalDateTime lastUpdate;
}