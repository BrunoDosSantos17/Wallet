package com.brunoSantos.position.domain;

import com.brunoSantos.asset.domain.Asset;
import com.brunoSantos.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @ManyToOne
    private Wallet wallet;

    @ManyToOne
    private Asset asset;

    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate date;
    private BigDecimal averagePrice;
}
