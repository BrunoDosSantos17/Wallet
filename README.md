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
