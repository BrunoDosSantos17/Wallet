package com.brunoSantos.wallet_app.shared.testing;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IsolatedPostgresTest {
    @Test
    void permits_only_explicitly_named_test_databases_without_options() {
        assertThat(IsolatedPostgres.validate("jdbc:postgresql://localhost:5432/wallet_test_ci"))
                .endsWith("/wallet_test_ci");
        for (String unsafe : new String[]{"jdbc:postgresql://localhost:5433/wallet",
                "jdbc:postgresql://localhost:5432/wallet_test?options=-csearch_path%3Dpublic",
                "jdbc:postgresql://localhost:5432/wallet_test_evil/other", "jdbc:h2:mem:test"}) {
            assertThatThrownBy(() -> IsolatedPostgres.validate(unsafe)).isInstanceOf(IllegalStateException.class);
        }
    }
}
