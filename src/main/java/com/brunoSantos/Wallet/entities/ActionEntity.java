package com.brunoSantos.Wallet.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "action")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class ActionEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Integer id;

    private String name;

    private BigDecimal value;

    private int qtd;

}
