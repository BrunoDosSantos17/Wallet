package com.brunoSantos.wallet_app.shared.testing;

/** Guard against accidentally running database-writing integration tests on wallet. */
public final class IsolatedPostgres {
    private IsolatedPostgres() {}

    public static String url() {
        return validate(System.getenv("C3_TEST_DB_URL"));
    }

    static String validate(String url) {
        if (url == null || !url.matches("jdbc:postgresql://[A-Za-z0-9._-]+(?::[0-9]{1,5})?/wallet_test(?:_[a-z0-9_]+)?")) {
            throw new IllegalStateException("C3_TEST_DB_URL deve apontar para banco PostgreSQL descartável wallet_test ou wallet_test_<sufixo>, sem parâmetros");
        }
        return url;
    }
}
