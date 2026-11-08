package com.brunoSantos.wallet_app.shared.exception;

import com.brunoSantos.wallet_app.position.domain.AssetPosition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPositionConflictHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimistic_lock_conflict_returns_http_409() {
        var response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException(AssetPosition.class, 1L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("alterada por outra operação");
    }

    @Test
    void duplicate_position_constraint_returns_http_409() {
        var exception = new DataIntegrityViolationException(
                "duplicate pair", new SQLException("unique conflict", "23505"));

        var response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("Já existe uma posição");
    }

    @Test
    void non_unique_integrity_failure_is_not_reported_as_concurrency_conflict() {
        var exception = new DataIntegrityViolationException(
                "foreign key conflict", new SQLException("foreign key conflict", "23503"));

        var response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
