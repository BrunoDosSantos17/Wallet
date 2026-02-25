package com.brunoSantos.asset.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Asset {

    @Id
    private String ticker;

    private String name;

    @Enumerated(EnumType.STRING)
    private AssetType type;

    private BigDecimal currentPrice;

    private LocalDateTime lastUpdate;
}