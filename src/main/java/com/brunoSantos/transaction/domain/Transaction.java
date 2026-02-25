package com.brunoSantos.transaction.domain;

import com.brunoSantos.asset.domain.Asset;
import com.brunoSantos.wallet.domain.Wallet;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
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

    private BigDecimal quantity;
    private BigDecimal price;
    private LocalDate date;

}
