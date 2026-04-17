# Backend Deployment Guide

This project uses Docker Compose to deploy the backend services and the application itself.

## Prerequisites

- Docker and Docker Compose installed.
- Java 17 (for local build if needed).

## Services

The `docker-compose.yml` file defines the following services:

- **mysql**: MySQL 8.0 database (`health_iot`).
- **redis**: Redis 7.2 cache.
- **zookeeper** & **kafka**: Message queue for alarm events.
- **emqx**: MQTT Broker (v5.3.0) for device connectivity.
- **tdengine**: Time-series database (v3.3.0.0) for sensor data.
- **backend**: The Spring Boot application.

## Configuration

The application is configured via environment variables in `docker-compose.yml`.
Key configurations:
- **MySQL**: `jdbc:mysql://mysql:3306/health_iot`
- **Redis**: `redis:6379`
- **Kafka**: `kafka:9092`
- **EMQX**: `tcp://emqx:1883` (MQTT), `http://emqx:18083` (API)
- **TDengine**: `jdbc:TAOS-RS://tdengine:6041/pension`

## Deployment Steps

1.  **Build the application**:
    ```bash
    ./gradlew clean build -x test
    ```

2.  **Start services**:
    ```bash
    docker-compose up -d
    ```
    This command will:
    - Start all infrastructure services (MySQL, Redis, Kafka, EMQX, TDengine).
    - Build the `backend` Docker image.
    - Start the `backend` container.

3.  **Verify Deployment**:
    - **Backend API**: http://localhost:8080/actuator/health
    - **EMQX Dashboard**: http://localhost:18083 (User: `admin`, Pass: `public`)
    - **TDengine**: Connect via client or check logs.

## Troubleshooting

- **Database Connection**: The backend waits for MySQL and Redis to be healthy. If it fails, check `docker-compose logs backend`.
- **TDengine**: The application attempts to create the `pension` database on startup. If this fails, ensure TDengine is running and accessible.
- **EMQX Auth**: The application uses the default `admin/public` credentials for API access. Ensure these match your EMQX configuration.
    - If device authentication fails, log in to EMQX Dashboard -> Authentication, and ensure `Built-in Database` is enabled as a data source.

## Data Persistence

Data is persisted in the `./data` directory in the project root.
- `./data/mysql`
- `./data/redis`
- `./data/kafka`
- `./data/emqx`
- `./data/tdengine`
