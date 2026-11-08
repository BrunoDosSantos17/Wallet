package com.brunoSantos.wallet_app.transaction.repository;

import com.brunoSantos.wallet_app.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface TransactionRepository  extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByWalletIdAndImportIdentity(Long walletId, String importIdentity);

    @Query("select max(t.dataTransaction) from Transaction t where t.wallet.id = :walletId and t.asset.ticker = :ticker")
    LocalDate findLatestDate(@Param("walletId") Long walletId, @Param("ticker") String ticker);
}
