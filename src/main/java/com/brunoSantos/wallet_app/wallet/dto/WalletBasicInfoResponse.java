package com.brunoSantos.wallet_app.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletBasicInfoResponse(
        BigDecimal patrimonioTotal,
        BigDecimal rendaMensalEst,
        Integer ativosCarteira,
        List<AssetDistribution> distribuicao,
        List<String> avisos
) {
    public record AssetDistribution(
            String nomeAtivo,
            String percentual,
            String valor,
            String quantidade,
            Integer quantidadeAtivos
    ) {}
}
