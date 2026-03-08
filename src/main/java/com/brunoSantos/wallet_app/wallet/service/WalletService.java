package com.brunoSantos.wallet_app.wallet.service;

import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import com.brunoSantos.wallet_app.wallet.dto.UpdateWalletRequest;
import com.brunoSantos.wallet_app.wallet.exception.WalletExistsException;
import com.brunoSantos.wallet_app.wallet.exception.WalletNotFoundException;
import com.brunoSantos.wallet_app.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public Wallet createWallet(String name) {

        if(walletRepository.findByName(name).isPresent()) {
            throw new WalletExistsException();
        };

        return walletRepository.save(Wallet.builder().name(name).build());
    }

    public Wallet findById(Long id) {
        return walletRepository.findById(id)
                .orElseThrow(WalletNotFoundException::new);
    }

    public List<Wallet> findAll() {
        return walletRepository.findAll();
    }

    public void delete(Long id) {
        walletRepository.delete(findById(id));
    }

    public Wallet update(UpdateWalletRequest updateWallet) {
        var wallet =  walletRepository.findById(updateWallet.id())
                .orElseThrow(WalletNotFoundException::new);

        wallet.setName(updateWallet.name());

        return walletRepository.save(wallet);

    }
}
