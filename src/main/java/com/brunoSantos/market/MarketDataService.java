package com.brunoSantos.market;

import com.brunoSantos.market.dto.MarketDataServiceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "stock", url="https://brapi.dev/api")
public interface MarketDataService {

    @GetMapping("/quote/{stockId}")
    MarketDataServiceDto getStock(@RequestParam("token") String token,
                                  @PathVariable("stockId") String stockId);

}
