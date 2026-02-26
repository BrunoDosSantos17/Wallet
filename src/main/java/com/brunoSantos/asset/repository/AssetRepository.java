package com.brunoSantos.asset.repository;

import com.brunoSantos.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Integer> {

    Optional<Asset> findByTicker(String ticker);
}
