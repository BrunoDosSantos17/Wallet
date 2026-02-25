package com.brunoSantos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class WalletApplication {

	static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}

}
