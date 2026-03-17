# 💼 Wallet App

A REST API for managing investment wallets, built with Spring Boot. It allows users to create wallets, register BUY/SELL transactions on assets, and track positions with real-time price data via the [Brapi](https://brapi.dev) integration.

## 🚀 Technologies

- Java 25
- Spring Boot 4.0.1
- Spring Data JPA
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

## 📡 Endpoints

### Wallet

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/wallet` | Create a wallet |
| GET | `/wallet` | List all wallets |
| GET | `/wallet/{id}` | Get wallet by ID |
| PUT | `/wallet` | Rename a wallet |
| DELETE | `/wallet/{id}` | Delete a wallet |

### Transactions

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/transactions` | Register a BUY or SELL transaction |

Transaction types: `BUY`, `SELL`

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
