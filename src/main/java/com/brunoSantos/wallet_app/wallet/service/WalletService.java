package com.brunoSantos.wallet_app.wallet.service;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public Wallet createWallet(String name) {
        return walletRepository.save(Wallet.builder().name(name).build());
    }

    public Wallet findById(Long id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }

    public List<Wallet> findAll() {
        return walletRepository.findAll();
    }

    public void delete(Long id) {
        walletRepository.delete(findById(id));
    }
}
