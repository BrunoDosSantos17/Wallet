package com.brunoSantos.wallet_app.integration.brapi.dto;

import java.util.List;

public record BrapiResponse(
        List<BrapiStock> results
) {}
