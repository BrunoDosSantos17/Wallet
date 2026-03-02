package com.brunoSantos.wallet_app.transaction.repository;

import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository  extends JpaRepository<Transaction, Integer> {
}
