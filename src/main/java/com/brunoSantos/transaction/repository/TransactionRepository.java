package com.brunoSantos.transaction.repository;

import com.brunoSantos.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository  extends JpaRepository<Transaction, Integer> {
}
