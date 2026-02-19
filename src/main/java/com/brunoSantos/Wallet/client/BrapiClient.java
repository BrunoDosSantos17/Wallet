package com.brunoSantos.Wallet.client;

import com.brunoSantos.Wallet.client.dto.BrapiResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "stock", url="https://brapi.dev/api")
public interface BrapiClient {

    @GetMapping("/quote/{stockId}")
    BrapiResponseDto getStock(@RequestParam("token") String token,
                                        @PathVariable("stockId") String stockId);
}
