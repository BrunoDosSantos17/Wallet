package com.brunoSantos.wallet_app.imports.dto;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
class ImportErrorDetailTest {

    @Test
    void should_create_error_detail_with_row_number_and_message() {
        ImportErrorDetail detail = new ImportErrorDetail(5, "Data inválida");

        assertThat(detail.rowNumber()).isEqualTo(5);
        assertThat(detail.message()).isEqualTo("Data inválida");
    }

    @Test
    void should_have_equality_based_on_fields() {
        ImportErrorDetail detail1 = new ImportErrorDetail(10, "Erro teste");
        ImportErrorDetail detail2 = new ImportErrorDetail(10, "Erro teste");

        assertThat(detail1).isEqualTo(detail2);
    }

    @Test
    void should_have_different_equality_when_fields_differ() {
        ImportErrorDetail detail1 = new ImportErrorDetail(10, "Erro A");
        ImportErrorDetail detail2 = new ImportErrorDetail(10, "Erro B");

        assertThat(detail1).isNotEqualTo(detail2);
    }
}
