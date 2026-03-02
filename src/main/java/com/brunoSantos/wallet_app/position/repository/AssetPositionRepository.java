package com.brunoSantos.wallet_app.position.repository;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetPositionRepository extends JpaRepository<AssetPosition, Integer> {

    Optional<AssetPosition> findByWalletAndAsset(Wallet wallet, Asset asset);

}
