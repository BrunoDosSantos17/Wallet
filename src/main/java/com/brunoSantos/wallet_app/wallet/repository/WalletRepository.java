package com.brunoSantos.wallet_app.wallet.repository;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
}
