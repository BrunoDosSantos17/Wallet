# 💼 Wallet App

A REST API for managing investment wallets, built with Spring Boot. It allows users to create wallets, register BUY/SELL transactions on assets, and track positions with real-time price data via the [Brapi](https://brapi.dev) integration.

The API documentation is available through **Scalar** and is generated from the OpenAPI specification.

## 🚀 Technologies

- Java 25
- Spring Boot 4.0.1
- Spring Data JPA
- Springdoc OpenAPI
- Scalar API Reference
- PostgreSQL
- Flyway (schema migrations)
- H2 (tests)
- Lombok
- Maven

## ⚙️ Getting Started

### Prerequisites

- Java 25+
- Docker & Docker Compose
- A [Brapi](https://brapi.dev) API token

### 1. Start the database

```bash
docker-compose -f src/main/resources/docker/docker-compose.yml up -d
```

### 2. Set the environment variable

Create a `.env` file or export the variable:

```env
BRAPI_TOKEN=your_token_here
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### Database migrations

Flyway creates and upgrades the PostgreSQL schema on application startup. The
initial `src/main/resources/db/migration/V1__create_initial_schema.sql` is a
complete schema for a **new, empty database**; Hibernate only validates it.
Flyway records applied versions and checksums in `flyway_schema_history`.

Existing non-empty databases without Flyway history are not automatically
baselined or recreated. This version does not migrate their data. To start over,
explicitly select and confirm the database/schema to replace, stop writers, and
recreate only that target before starting the application. This destroys its
contents. Never point tests or destructive commands at a database containing
data you want to keep. Legacy manual scripts are preserved in
`src/main/resources/db/legacy-migration` and are not run by Flyway.

For later schema changes, add a new `V2__description.sql`, `V3__description.sql`,
etc. under `db/migration`; never edit an already applied migration. Review and
test it against an isolated PostgreSQL database before deployment. Flyway clean
is disabled. Do not use `repair` to hide checksum or migration failures; first
investigate the cause and determine a reviewed recovery procedure.

PostgreSQL integration tests require an isolated, disposable database supplied by
`C3_TEST_DB_URL`, with `C3_TEST_DB_USER` and `DB_PASSWORD`. The PostgreSQL tests
run migrations and validate the mappings; the H2 test profile uses Hibernate
separately and does not validate PostgreSQL migrations.

The JDBC URL guard requires a database named `wallet_test` or
`wallet_test_<suffix>`, without URL parameters. This is a safety check, **not**
proof that a database contains no personal data: provision an empty disposable
database with test-only credentials and never point it to a real environment.
Tests create, update and delete records and schemas in the target database.
The CI workflow starts PostgreSQL 17 and runs the required T1 integration tests
with a check that none were skipped. For a local disposable instance:

```bash
export C3_TEST_DB_URL=jdbc:postgresql://localhost:5432/wallet_test
export C3_TEST_DB_USER=wallet_test
export DB_PASSWORD='<test-only-password>'
mvn -Dtest=FinancialPrecisionTest,PostgresPrecisionLimitsTest,TransactionConcurrencyTest,WalletLifecycleTest,FlywaySchemaPostgresTest test
mvn clean install
```

`DELETE /wallet/{id}` removes that wallet's positions, transactions and dividend
records in one transaction: the application deletes dividends first, then database
foreign-key cascades remove positions and transactions. Direct deletion of a
position with dividends remains blocked. Shared assets and other wallets are
preserved. This behavior requires Flyway migration V5; review and authorize
its deployment separately before using it in an existing database.

### Estimated income synchronization

`POST /dividendos/posicoes/{id}/sincronizar?inicio=YYYY-MM-DD` synchronizes
Brazilian stock dividends/JCP or FII income through today. It uses Brapi's
unadjusted per-unit value and transaction quantities through the record date.
The Brapi plan must allow the relevant endpoints and unadjusted values. Values
are **gross estimates, not confirmed cash credits**; missing historical trades,
corporate actions and JCP taxes can make them inaccurate. FII amortizations are
not income and are excluded. `rendaMensalEst` sums stored calculated values whose
payment dates fall in the current month in São Paulo, including closed positions.
It returns zero when no records are available, even if synchronization failed or
never ran; it does not refresh providers on read. Existing US stock positions
can synchronize only the latest payment returned by the free
[SmarterDividends API](https://smarterdividends.com/dividend-api): earlier payments
in the same month may be missing. The source's ex-dividend calendar provides the
last eligible purchase date. US amounts remain in USD in storage and are converted
on summary read using the current USD/BRL bid. If unavailable, the USD portion is
zero and `avisos` contains `ConversaoMoedaIndisponivel`; BRL amounts are retained.
For a US stock operation, send `assetType: "stock_abroad"` to `POST /transactions`;
the ticker is checked through SmarterDividends and transaction prices are in USD.
The import spreadsheet still uses Brapi and does not support this explicit routing.
US stock market prices are not refreshed or converted for the portfolio valuation:
new stocks have no available BRL quote and therefore contribute zero under the
current missing-price policy. This does not affect USD dividend conversion.

In the wallet summary, distribution `quantidade` is a decimal string preserving
fractional units, while `quantidadeAtivos` counts nonzero positions in the class.
Missing market prices are treated as zero without a separate indicator.

The source has no stable event ID. Events with the same type and record date for
one position, or a change in a previously stored event or its historical quantity,
cause a conflict requiring manual review; the synchronization is transactional.
The chosen start date filters by payment date, not record date. Incomplete or
unavailable Brapi data fails rather than being represented as zero.

## 📚 API Documentation

- Scalar UI: [http://localhost:8080/scalar](http://localhost:8080/scalar)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

The Scalar page loads its interface from jsDelivr, so the browser needs internet access to display it.

## 📡 Endpoints

### Wallet

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/wallet` | Create a wallet |
| GET | `/wallet` | List all wallets |
| GET | `/wallet/all` | List all wallets (explicit alias) |
| GET | `/wallet/{id}` | Get wallet by ID |
| PUT | `/wallet` | Rename a wallet |
| DELETE | `/wallet/{id}` | Delete a wallet |
| GET | `/wallet/infoBasica/{walletId}` | Get wallet summary and asset allocation |
| GET | `/wallet/infoBasica/{walletId}/atualizar` | Refresh prices for assets in the wallet via Brapi and return the summary |
| POST | `/wallet/{walletId}/import?file={xlsx}` | Import transactions from an XLSX file |
| POST | `/wallet/scheduler/asset-price/{acao}` | Enable or disable automatic asset price updates (`ATIVAR` or `DESATIVAR`) |

### Transactions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/transactions` | Register a BUY or SELL transaction |

Transaction types: `BUY`, `SELL`

### Automatic price updates

Automatic price updates are enabled when the application starts. The first update runs after five minutes, then subsequent updates run five minutes after each execution completes. All registered assets are updated through Brapi; a failure for one ticker does not stop updates for the others.

Use the scheduler endpoint to change this behavior:

```http
POST /wallet/scheduler/asset-price/ATIVAR
POST /wallet/scheduler/asset-price/DESATIVAR
```

Manual synchronization with `GET /wallet/infoBasica/{walletId}/atualizar` remains available regardless of whether automatic updates are enabled.

The schedule delay values are configurable in milliseconds:

| Property | Default |
|----------|---------|
| `wallet.assets.price-update-initial-delay` | `300000` (5 minutes) |
| `wallet.assets.price-update-delay` | `300000` (5 minutes) |

Set these as Spring environment properties (for example, `WALLET_ASSETS_PRICE_UPDATE_DELAY=60000`).

## 🧪 Running Tests

### Bounded XLS/XLSX import (R4)

`POST /wallet/{walletId}/import` accepts multipart field `file`: at most **10 MiB**
per file and **11 MiB** per request (binary units, matching the UI's nominal 10 MB
limit). The backend checks extension **and** Excel content, the first-row header,
and workbook structure. Only one sheet, 20 columns and 5,000 data rows are allowed;
the last row/column index also respects these bounds, including formatted blank
rows/cells. XLSX expansion is capped at 50 MiB, counting actual ZIP output before
opening a workbook. XLS uses POI's streaming BIFF event preflight. A bounded
temporary file is removed after parsing or rejection; no workbook is opened for
an out-of-bounds structure. POI's existing ZIP safety checks remain enabled.

Required columns (zero-based): 0 `Data`/`Data do Negócio`, 1 `Tipo`/`Tipo de
Movimentação`, 5 `Ticker`/`Código de Negociação`, 6 `Quantidade`, 7 `Preço`.
Headers ignore case and accents. Empty files/sheets and invalid headers are
rejected. Numeric cells and decimal text (`25.50`, `25,50`, `1.234,56`) are
accepted; formulas anywhere in a data row reject that row **without evaluation**.
Native Excel dates and text `dd/MM/yyyy` are accepted.

At most two uploads are active per application instance. Groups run on four
workers with a queue of 100; ticker order within each upload is preserved.
Saturation before any group is accepted returns **503** with no financial work
submitted for that upload. Rejected groups after processing starts become
per-row errors; the request waits for accepted groups and reports actual partial
results. Multipart size rejection is **413**; invalid workbook/content is **400**.
There is no global import timeout or claim that disconnects/timeouts undo commits.
Deployments with multiple instances must enforce any cluster-wide quotas separately.

For requests rejected from their declared Content-Length before the body is
consumed, the servlet container's bounded discard policy may close the connection
before the client receives a 413. Clients must also handle transport errors; the
backend does not disable that safety to drain unbounded oversized bodies.

Brapi and AwesomeAPI have explicit 3-second connection and 10-second read
timeouts per HTTP call. Successful ticker validation is reused only within the
current upload; failures are not cached. C4 remains unchanged: at most two
attempts per row per upload, duplicates without retry, individual operation
commits, and persisted idempotency identities preventing repeated financial writes.
Legacy rows with null import identity remain outside automatic deduplication.

R4 tests: `mvn -Dtest=ImportResourceLimitsTest,ImportExecutorConfigurationTest,ImportUploadLimitsTest,BrapiTimeoutTest,AwesomeApiTimeoutTest test`.
The HTTP multipart tests start only an isolated random-port server with mocked
persistence and no datasource. Provider timeout tests use localhost only.
`ImportResourcePostgresTest` uses a synthetic workbook, real commits and a mocked
provider; it requires the guarded disposable PostgreSQL environment described
above (no personal XLSX). Run it with `mvn -Dtest=ImportResourcePostgresTest test`.
JaCoCo (Java 25 compatible) produces `target/site/jacoco/{index.html,jacoco.xml}`
with `mvn verify`; skipped PostgreSQL tests do **not** establish persistence gates.

```bash
./mvnw test
```

## 🗄️ Database Config

| Property | Value |
|----------|-------|
| Host | `localhost:5433` |
| Database | `wallet` |
| Username | `proposaldb` |
| Password | `docker` |
