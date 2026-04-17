# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Health IoT monitoring system (Spring Boot 3.3.8 + Java 17 backend, Vue 3/TypeScript frontend). Manages elderly care devices, health data, and alarms via MQTT, Kafka, and TDengine.

## Build & Run Commands

### Backend

```bash
# Build (skip tests)
./gradlew clean build -x test

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "com.ncf.demo.DemoApplicationTests"

# Run locally (requires local profile)
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Frontend (demo_ui/)

```bash
npm install
npm run dev       # Dev server
npm run build     # Production build (vue-tsc + vite)
npm run preview   # Preview production build
```

### Full Stack (Docker)

```bash
docker-compose up -d   # Start all services
docker-compose logs -f demo-backend  # Watch logs
```

## Architecture

### Backend (`src/main/java/com/ncf/demo/`)

**Data flow:** IoT device → MQTT (EMQX) → `MqttMessageService` → `DeviceDataParserService` → MySQL (JPA) + TDengine (time-series) → Kafka alarm events → `AlarmManagementService`

**Key layers:**
- `web/` — REST controllers + DTOs (`ApiResponse<T>` wrapper)
- `service/` — Business logic; `AlarmManagementService` triggers alarms, `TdengineService` queries sensor history
- `entity/` + `repository/` — JPA entities with Spring Data repositories
- `config/` — Bean configuration for MQTT, Kafka, security, thread pools
- `domain/` — Enums (AlarmType, DeviceType, UserRole, etc.)

**Security:** JWT via `JwtAuthFilter` + `JwtService`. Public routes: `/api/login`, `/api/register`, `/api/emqx/**`. Admin routes: `/api/admin/**`. Rate limiting via `RateLimitFilter`.

**MQTT:** Spring Integration inbound channel adapters receive device telemetry and disconnect events. `MqttGateway` is the outbound interface for device commands.

**Conditional beans:** Kafka consumer/producer beans are conditional on `app.kafka.enabled=true`. TDengine service creates the `pension` database on startup if absent.

### Frontend (`demo_ui/src/`)

Vue 3 SPA with Element Plus UI, ECharts for charts, Pinia for state, Vue Router, and Axios for API calls.

## Configuration

**Local dev:** `application-local.properties` uses H2 in-memory DB (no external dependencies needed).
**Tests:** `application-test.properties` uses H2 with `create-drop` DDL; `MqttGateway` is mocked.
**Production:** Requires MySQL (port 3307), Redis (6379), Kafka (9092), EMQX (1883), TDengine (6041).

Default admin credentials: `admin` / `Admin@123` (override via `ADMIN_PASSWORD_HASH` env var).

Frontend API base URL configured in `demo_ui/.env.local`.
