package com.brunoSantos.wallet_app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
public class WalletApplication {

	static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}

}
