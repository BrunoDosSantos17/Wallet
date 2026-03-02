package com.brunoSantos.wallet_app.asset.repository;

import com.brunoSantos.wallet_app.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Integer> {

    Optional<Asset> findByTicker(String ticker);
}
