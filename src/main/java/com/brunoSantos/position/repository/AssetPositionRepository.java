package com.brunoSantos.position.repository;

import com.brunoSantos.asset.domain.Asset;
import com.brunoSantos.position.domain.AssetPosition;
import com.brunoSantos.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetPositionRepository extends JpaRepository<AssetPosition, Integer> {

    Optional<AssetPosition> findByWalletAndAsset(Wallet wallet, Asset asset);

}
