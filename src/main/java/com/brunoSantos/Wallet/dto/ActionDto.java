package com.brunoSantos.Wallet.dto;

import java.math.BigDecimal;

public record ActionDto(String name, BigDecimal value, int qtd) {
}
