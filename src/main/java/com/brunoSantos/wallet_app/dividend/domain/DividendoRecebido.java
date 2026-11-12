package com.brunoSantos.wallet_app.dividend.domain;

import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import com.brunoSantos.wallet_app.shared.financial.FinancialPrecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "dividendo_recebido")
@Getter
@NoArgsConstructor
public class DividendoRecebido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asset_position", nullable = false)
    private AssetPosition assetPosition;

    @Column(nullable = false, precision = FinancialPrecision.PRICE_PRECISION, scale = FinancialPrecision.PRICE_SCALE)
    private BigDecimal valorDividendo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoProvento tipoProvento;

    @Column(nullable = false)
    private LocalDate dataPagamento;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant dataInsercao;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant dataUpdate;

    @Column(length = 255)
    private String identificadorOrigem;

    private LocalDate dataBase;

    @Column(precision = FinancialPrecision.QUANTITY_PRECISION, scale = FinancialPrecision.QUANTITY_SCALE)
    private BigDecimal quantidadeElegivel;

    @Column(precision = FinancialPrecision.QUANTITY_PRECISION, scale = FinancialPrecision.QUANTITY_SCALE)
    private BigDecimal valorUnitario;

    @Column(length = 64)
    private String assinaturaEvento;

    @Column(nullable = false, length = 3)
    private String moeda = "BRL";

    public DividendoRecebido(AssetPosition position, BigDecimal valor, TipoProvento tipo, LocalDate pagamento,
                            String identificador, LocalDate base, BigDecimal quantidade, BigDecimal unitario,
                            String assinatura) {
        this.assetPosition = position;
        this.valorDividendo = valor;
        this.tipoProvento = tipo;
        this.dataPagamento = pagamento;
        this.identificadorOrigem = identificador;
        this.dataBase = base;
        this.quantidadeElegivel = quantidade;
        this.valorUnitario = unitario;
        this.assinaturaEvento = assinatura;
    }

    public void setMoeda(String moeda) { this.moeda = moeda; }
}
