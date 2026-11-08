package com.brunoSantos.wallet_app.transaction.controller;

import com.brunoSantos.wallet_app.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void should_reject_missing_required_fields_at_the_rest_boundary() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void should_reject_non_positive_quantity_and_price_at_the_rest_boundary() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType("application/json")
                        .content("""
                                {"walletId":1,"ticker":"PETR4","type":"BUY",
                                 "quantity":0,"price":-1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }
}
