package com.brunoSantos.wallet_app.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletBasicInfoResponse(
        BigDecimal patrimonioTotal,
        BigDecimal rendaMensalEst,
        Integer ativosCarteira,
        List<AssetDistribution> distribuicao
) {
    public record AssetDistribution(
            String nomeAtivo,
            String percentual,
            String valor,
            Integer quantidade
    ) {}
}
