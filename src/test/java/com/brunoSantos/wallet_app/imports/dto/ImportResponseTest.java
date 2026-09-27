package com.brunoSantos.wallet_app.imports.dto;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
class ImportResponseTest {

    @Test
    void should_create_response_with_total_success() {
        ImportResponse response = new ImportResponse(100, 100, 0, Collections.emptyList());

        assertThat(response.totalRows()).isEqualTo(100);
        assertThat(response.successCount()).isEqualTo(100);
        assertThat(response.errorCount()).isEqualTo(0);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void should_create_response_with_partial_success() {
        ImportErrorDetail error1 = new ImportErrorDetail(5, "Data inválida");
        ImportErrorDetail error2 = new ImportErrorDetail(10, "Ticker vazio");
        List<ImportErrorDetail> errors = List.of(error1, error2);

        ImportResponse response = new ImportResponse(100, 98, 2, errors);

        assertThat(response.totalRows()).isEqualTo(100);
        assertThat(response.successCount()).isEqualTo(98);
        assertThat(response.errorCount()).isEqualTo(2);
        assertThat(response.errors()).hasSize(2);
        assertThat(response.errors()).containsExactly(error1, error2);
    }

    @Test
    void should_have_equality_based_on_fields() {
        ImportResponse response1 = new ImportResponse(50, 50, 0, Collections.emptyList());
        ImportResponse response2 = new ImportResponse(50, 50, 0, Collections.emptyList());

        assertThat(response1).isEqualTo(response2);
    }
}
