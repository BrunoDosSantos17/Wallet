package com.brunoSantos.wallet_app.wallet.repository;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByName(String name);
}
