# Docker Compose Setup

This document describes how to run all services using Docker Compose.

## Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose)
- At least 4GB of available RAM

## Quick Start

1. **Build and start all services:**
   ```bash
   docker-compose up --build
   ```

2. **Start in detached mode (background):**
   ```bash
   docker-compose up -d --build
   ```

3. **View logs:**
   ```bash
   # All services
   docker-compose logs -f
   
   # Specific service
   docker-compose logs -f user-service
   ```

4. **Stop all services:**
   ```bash
   docker-compose down
   ```

5. **Stop and remove volumes (clean slate):**
   ```bash
   docker-compose down -v
   ```

## Services

All services are accessible on `localhost` with the following ports:

- **Workload API Service**: http://localhost:8080
- **User Service**: http://localhost:8081
- **Photo Service**: http://localhost:8082
- **Print Service**: http://localhost:8083

### PostgreSQL Databases

- **User Service DB**: `postgres-user:5432` (internal), `localhost:5432` (external)
- **Photo Service DB**: `postgres-photo:5432` (internal), `localhost:5433` (external)
- **Print Service DB**: `postgres-print:5432` (internal), `localhost:5434` (external)

## Service Dependencies

Services start in the following order:
1. PostgreSQL databases (with health checks)
2. Workload API Service
3. User Service
4. Photo Service
5. Print Service

## Environment Variables

You can override environment variables by creating a `.env` file in the root directory:

```env
# CA Configuration
CA_KEY_STORE_PATH=/app/ca-keys
CA_TRUST_DOMAIN=example.org

# Database Passwords (change in production!)
POSTGRES_PASSWORD=postgres

# Delegation Token Signing Key (change in production!)
DELEGATION_SIGNING_KEY=your-secure-random-key-min-256-bits
```

## Volumes

Docker Compose creates persistent volumes for:
- PostgreSQL data (survives container restarts)
- CA keys (stored in `workload-ca-keys` volume)

## Troubleshooting

### Check service status:
```bash
docker-compose ps
```

### Restart a specific service:
```bash
docker-compose restart user-service
```

### View service logs:
```bash
docker-compose logs user-service
```

### Access a service shell:
```bash
docker-compose exec user-service sh
```

### Rebuild a specific service:
```bash
docker-compose build user-service
docker-compose up -d user-service
```

## Network

All services are on the `spiffe-network` bridge network and can communicate using service names:
- `http://user-service:8081`
- `http://photo-service:8082`
- `http://print-service:8083`
- `http://workload-api-service:8080`

## Health Checks

PostgreSQL databases have health checks to ensure they're ready before services start.

## Production Considerations

For production deployment:
1. Change all default passwords
2. Use secure random keys for delegation signing
3. Use secrets management (Docker secrets, Vault, etc.)
4. Enable SSL/TLS for database connections
5. Use proper CA key storage (HSM/Vault) instead of filesystem
6. Set resource limits in docker-compose.yml
7. Use production-grade PostgreSQL configuration


