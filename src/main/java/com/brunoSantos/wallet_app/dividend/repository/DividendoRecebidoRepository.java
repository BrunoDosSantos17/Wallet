package com.brunoSantos.wallet_app.dividend.repository;

import com.brunoSantos.wallet_app.dividend.domain.DividendoRecebido;
import com.brunoSantos.wallet_app.dividend.domain.TipoProvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DividendoRecebidoRepository extends JpaRepository<DividendoRecebido, Long> {
    List<DividendoRecebido> findByAssetPositionIdAndTipoProvento(Long positionId, TipoProvento tipoProvento);

    Optional<DividendoRecebido> findByAssetPositionIdAndIdentificadorOrigem(Long positionId, String identificadorOrigem);

    @Query("select sum(d.valorDividendo) from DividendoRecebido d where d.assetPosition.wallet.id = :walletId "
            + "and d.dataPagamento >= :inicio and d.dataPagamento < :fim")
    BigDecimal sumByWalletAndPaymentPeriod(@Param("walletId") Long walletId,
                                          @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("select sum(d.valorDividendo) from DividendoRecebido d where d.assetPosition.wallet.id = :walletId "
            + "and d.dataPagamento >= :inicio and d.dataPagamento < :fim and d.moeda = :moeda")
    BigDecimal sumByWalletAndPeriodAndCurrency(@Param("walletId") Long walletId, @Param("inicio") LocalDate inicio,
                                               @Param("fim") LocalDate fim, @Param("moeda") String moeda);
}
