package com.brunoSantos.wallet_app.transaction.domain;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Wallet wallet;

    @ManyToOne
    private Asset asset;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(precision = FinancialPrecision.QUANTITY_PRECISION, scale = FinancialPrecision.QUANTITY_SCALE)
    private BigDecimal quantity;

    @Column(precision = FinancialPrecision.PRICE_PRECISION, scale = FinancialPrecision.PRICE_SCALE)
    private BigDecimal price;

    private LocalDate date;

    private LocalDate dataTransaction;

    @Column(length = 64)
    private String importIdentity;

}
