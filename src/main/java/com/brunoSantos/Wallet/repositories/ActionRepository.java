package com.brunoSantos.Wallet.repositories;


import com.brunoSantos.Wallet.entities.ActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionRepository extends JpaRepository<ActionEntity, Integer> {
}
