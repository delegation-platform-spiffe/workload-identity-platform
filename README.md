# SPIFFE-like Authentication & Delegation Framework

A demonstration of a SPIFFE-inspired authentication and delegation framework using Java and Spring Boot, eliminating the need for traditional OAuth2/OIDC client IDs and secrets.

## Project Structure

This repository contains multiple Maven projects, each designed to be a separate GitHub repository:

```
spiffe-like/
├── spiffe-common/          # Shared library (common components)
├── workload-api-service/   # Certificate Authority & Workload API
├── user-service/           # Authentication & delegation token issuer
├── photo-service/          # Photo storage service
└── print-service/          # Photo printing service
```

## Services

### 1. Workload API Service (Port 8080)
Certificate Authority that issues short-lived X.509 certificates to services.
- **CA Key Storage**: Filesystem (`./ca-keys/`) for local development
- **Endpoints**: `/workload/v1/health`, `/workload/v1/attest`, `/workload/v1/certificates`

### 2. User Service (Port 8081)
Handles user authentication and issues delegation tokens.
- **Database**: PostgreSQL (`user_service`)
- **Endpoints**: `/auth/register`, `/auth/login`, `/auth/delegate`, `/auth/validate`

### 3. Photo Service (Port 8082)
Stores and retrieves photos for users.
- **Database**: PostgreSQL (`photo_service`)
- **Endpoints**: `/photos`, `/photos/{id}`, `/photos/users/{userId}`, `/photos/print-status/{printJobId}`
- **mTLS Port**: 8443 (for service-to-service communication)

### 4. Print Service (Port 8083)
Handles photo printing with delegated access.
- **Database**: PostgreSQL (`print_service`)
- **Endpoints**: `/print`, `/print/status/{id}`
- **mTLS Port**: 8444 (for service-to-service communication)

## Quick Start

### Option 1: Docker Compose (Recommended)

The easiest way to run all services is using Docker Compose:

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode
docker-compose up -d --build

# View logs
docker-compose logs -f

# View logs for a specific service
docker-compose logs -f workload-api-service

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

All services will be available on `localhost`:
- Workload API: http://localhost:8080
- User Service: http://localhost:8081
- Photo Service: http://localhost:8082
- Print Service: http://localhost:8083

See [README-DOCKER.md](./README-DOCKER.md) for detailed Docker instructions.

### Option 2: Local Development

#### Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL 12+

#### Setup

1. **Start PostgreSQL** and create databases:
```sql
CREATE DATABASE user_service;
CREATE DATABASE photo_service;
CREATE DATABASE print_service;
```

2. **Build all projects** (in order):
```bash
# Build spiffe-common first (required by other services)
cd spiffe-common && mvn clean install

# Build workload-api-service
cd ../workload-api-service && mvn clean install

# Build user-service
cd ../user-service && mvn clean install

# Build photo-service
cd ../photo-service && mvn clean install

# Build print-service
cd ../print-service && mvn clean install
```

3. **Run services** (in separate terminals):
```bash
# Terminal 1: Workload API
cd workload-api-service && mvn spring-boot:run

# Terminal 2: User Service
cd user-service && mvn spring-boot:run

# Terminal 3: Photo Service
cd photo-service && mvn spring-boot:run

# Terminal 4: Print Service
cd print-service && mvn spring-boot:run
```

## API Documentation

### Workload API Service (Port 8080)

#### 1. Health Check
**GET** `/workload/v1/health`

**Request:**
```bash
curl http://localhost:8080/workload/v1/health
```

**Response:**
```json
{
  "status": "healthy"
}
```

#### 2. Attest Service Identity
**POST** `/workload/v1/attest`

**Request:**
```bash
curl -X POST http://localhost:8080/workload/v1/attest \
  -H "Content-Type: application/json" \
  -d '{
    "service_name": "photo-service",
    "attestation_proof": {
      "token": "dev-token-photo-service-12345",
      "process_id": "12345",
      "service_name": "photo-service"
    }
  }'
```

**Response:**
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Note:** For local development, static tokens are used. In production, this would validate Kubernetes Service Account tokens, AWS IAM roles, or process-based attestation.

#### 3. Get Certificate Bundle
**GET** `/workload/v1/certificates`

**Request:**
```bash
curl -X GET "http://localhost:8080/workload/v1/certificates?service_name=photo-service" \
  -H "Authorization: Bearer <attestation_token>"
```

**Response:**
```json
{
  "svid": {
    "cert": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
    "spiffe_id": "spiffe://example.org/photo-service"
  },
  "ca_certs": [
    "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"
  ],
  "expires_at": 1766438694,
  "ttl": 3600
}
```

---

### User Service (Port 8081)

#### 1. Register User
**POST** `/auth/register`

**Request:**
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "testpass123"
  }'
```

**Response (Success):**
```json
{
  "user_id": "a324f59b-3428-42f2-9345-a350da3db84d",
  "username": "testuser",
  "message": "User registered successfully"
}
```

**Response (Error):**
```json
{
  "error": "Registration failed: duplicate key value violates unique constraint"
}
```

#### 2. User Login
**POST** `/auth/login`

**Request:**
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpass123"
  }'
```

**Response (Success):**
```json
{
  "message": "Login successful",
  "user_id": "a324f59b-3428-42f2-9345-a350da3db84d",
  "username": "testuser"
}
```

**Response (Error):**
```json
{
  "error": "Invalid credentials"
}
```

#### 3. Issue Delegation Token
**POST** `/auth/delegate`

**Request:**
```bash
curl -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
    "targetService": "photo-service",
    "permissions": ["read:photos", "write:photos"],
    "ttlSeconds": 900
  }'
```

**Response:**
```json
{
  "delegation_token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzcGlmZmU6Ly9leGFtcGxlLm9yZy91c2VyLXNlcnZpY2UiLCJpc3MiOiJzcGlmZmU6Ly9leGFtcGxlLm9yZy91c2VyLXNlcnZpY2UiLCJhdWQiOlsic3BpZmZlOi8vZXhhbXBsZS5vcmcvcGhvdG8tc2VydmljZSJdLCJ1c2VyX2lkIjoiYTMyNGY1OWItMzQyOC00MmYyLTkzNDUtYTM1MGRhM2RiODRkIiwicGVybWlzc2lvbnMiOlsicmVhZDpwaG90b3MiLCJ3cml0ZTpwaG90b3MiXSwiaWF0IjoxNzY2NDM3Nzk0LCJleHAiOjE3NjY0Mzg2OTR9...",
  "expires_in": 900
}
```

**Response (Error):**
```json
{
  "error": "user_id is required"
}
```

#### 4. Validate Delegation Token
**GET** `/auth/validate?token=<delegation_token>`

**Request:**
```bash
curl "http://localhost:8081/auth/validate?token=eyJhbGciOiJIUzUxMiJ9..."
```

**Response:**
```json
{
  "valid": true,
  "token": {
    "user_id": "a324f59b-3428-42f2-9345-a350da3db84d",
    "permissions": [
      "read:photos",
      "write:photos"
    ],
    "audience": [
      "spiffe://example.org/photo-service"
    ],
    "expires_at": 1766438695
  }
}
```

---

### Photo Service (Port 8082)

#### 1. Upload Photo
**POST** `/photos`

**Request:**
```bash
curl -X POST http://localhost:8082/photos \
  -F "file=@/path/to/image.png" \
  -F "userId=a324f59b-3428-42f2-9345-a350da3db84d"
```

**Response:**
```json
{
  "id": "8b06c1bd-f52c-4694-98c5-ccead96c5230",
  "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
  "photoBlob": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
  "filename": "image.png",
  "contentType": "image/png",
  "size": 70,
  "metadata": null,
  "createdAt": "2025-12-22T21:09:57.892674243Z"
}
```

#### 2. Get Photo by ID
**GET** `/photos/{photoId}?userId={userId}`

**Request:**
```bash
curl -o downloaded_image.png \
  "http://localhost:8082/photos/8b06c1bd-f52c-4694-98c5-ccead96c5230?userId=a324f59b-3428-42f2-9345-a350da3db84d"
```

**Response:**
- **Content-Type**: `image/png` (or appropriate image type)
- **Body**: Binary image data
- **Status**: 200 OK

**Error Response (404):**
```
Not Found
```

#### 3. List User Photos
**GET** `/photos/users/{userId}`

**Request:**
```bash
curl http://localhost:8082/photos/users/a324f59b-3428-42f2-9345-a350da3db84d
```

**Response:**
```json
[
  {
    "id": "96eaf367-f0c5-4c69-beab-b369ec408e3a",
    "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
    "photoBlob": "UE5HCg==",
    "filename": "test_image.png",
    "contentType": "image/png",
    "size": 4,
    "metadata": null,
    "createdAt": "2025-12-21T00:07:47.028019Z"
  },
  {
    "id": "8b06c1bd-f52c-4694-98c5-ccead96c5230",
    "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
    "photoBlob": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
    "filename": "image.png",
    "contentType": "image/png",
    "size": 70,
    "metadata": null,
    "createdAt": "2025-12-22T21:09:57.892674Z"
  }
]
```

#### 4. Check Print Status (mTLS Endpoint)
**GET** `/photos/print-status/{printJobId}`

This endpoint demonstrates mTLS communication between photo-service and print-service.

**Request:**
```bash
curl http://localhost:8082/photos/print-status/88d94043-f8b6-4cf3-9ed9-5797be2e40d6
```

**Response:**
```json
{
  "id": "88d94043-f8b6-4cf3-9ed9-5797be2e40d6",
  "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
  "photoIds": [
    "96eaf367-f0c5-4c69-beab-b369ec408e3a"
  ],
  "status": "COMPLETED",
  "createdAt": "2025-12-22T21:10:07.469693023Z",
  "completedAt": "2025-12-22T21:10:10.950127Z"
}
```

**Error Response:**
```json
{
  "error": "Invalid print job ID"
}
```

---

### Print Service (Port 8083)

#### 1. Create Print Job
**POST** `/print`

**Request:**
```bash
curl -X POST http://localhost:8083/print \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
    "photoIds": [
      "96eaf367-f0c5-4c69-beab-b369ec408e3a"
    ]
  }'
```

**Response:**
```json
{
  "id": "88d94043-f8b6-4cf3-9ed9-5797be2e40d6",
  "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
  "photoIds": [
    "96eaf367-f0c5-4c69-beab-b369ec408e3a"
  ],
  "status": "IN_PROGRESS",
  "createdAt": "2025-12-22T21:10:07.469693023Z",
  "completedAt": null
}
```

#### 2. Get Print Job Status
**GET** `/print/status/{printId}`

**Request:**
```bash
curl http://localhost:8083/print/status/88d94043-f8b6-4cf3-9ed9-5797be2e40d6
```

**Response (In Progress):**
```json
{
  "id": "88d94043-f8b6-4cf3-9ed9-5797be2e40d6",
  "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
  "photoIds": [
    "96eaf367-f0c5-4c69-beab-b369ec408e3a"
  ],
  "status": "IN_PROGRESS",
  "createdAt": "2025-12-22T21:10:07.469693023Z",
  "completedAt": null
}
```

**Response (Completed):**
```json
{
  "id": "88d94043-f8b6-4cf3-9ed9-5797be2e40d6",
  "userId": "a324f59b-3428-42f2-9345-a350da3db84d",
  "photoIds": [
    "96eaf367-f0c5-4c69-beab-b369ec408e3a"
  ],
  "status": "COMPLETED",
  "createdAt": "2025-12-22T21:10:07.469693023Z",
  "completedAt": "2025-12-22T21:10:10.950127Z"
}
```

**Error Response (404):**
```
Not Found
```

---

## Complete API Flow Example

Here's a complete example flow demonstrating the entire authentication and delegation system:

```bash
# 1. Register a new user
USER_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "securepass123"
  }')

# 2. Login
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "securepass123"
  }')

USER_ID=$(echo $LOGIN_RESPONSE | jq -r '.user_id')

# 3. Issue delegation token for photo-service
DELEGATION_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"targetService\": \"photo-service\",
    \"permissions\": [\"read:photos\", \"write:photos\"],
    \"ttlSeconds\": 900
  }")

DELEGATION_TOKEN=$(echo $DELEGATION_RESPONSE | jq -r '.delegation_token')

# 4. Upload a photo (using delegation token in Authorization header)
# Note: In production, the photo-service would validate the delegation token
curl -X POST http://localhost:8082/photos \
  -F "file=@photo.jpg" \
  -F "userId=$USER_ID"

# 5. List photos
curl http://localhost:8082/photos/users/$USER_ID

# 6. Issue delegation token for print-service
PRINT_DELEGATION=$(curl -s -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"targetService\": \"print-service\",
    \"permissions\": [\"read:photos\", \"print:photos\"],
    \"ttlSeconds\": 900
  }")

# 7. Create print job
PHOTO_ID=$(curl -s http://localhost:8082/photos/users/$USER_ID | jq -r '.[0].id')
curl -X POST http://localhost:8083/print \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"photoIds\": [\"$PHOTO_ID\"]
  }"

# 8. Check print status (via photo-service mTLS endpoint)
PRINT_JOB_ID=$(curl -s -X POST http://localhost:8083/print \
  -H "Content-Type: application/json" \
  -d "{\"userId\": \"$USER_ID\", \"photoIds\": [\"$PHOTO_ID\"]}" | jq -r '.id')

sleep 2
curl http://localhost:8082/photos/print-status/$PRINT_JOB_ID
```

## Architecture

See [ARCHITECTURE.md](./ARCHITECTURE.md) for detailed architecture documentation.

## Key Features

- ✅ **No Static Credentials**: Services use short-lived certificates
- ✅ **No Persistent Private Keys**: Service keys only in memory
- ✅ **Automatic Certificate Rotation**: Certificates refreshed at 80% of TTL
- ✅ **Delegation Tokens**: User-issued JWTs for service-to-service delegation
- ✅ **mTLS Support**: Mutual TLS for service-to-service communication
- ✅ **Static Token Attestation**: For local development (⚠️ NOT for production)

## Security Notes

### Local Development Mode
- **Static Attestation Tokens**: Pre-shared tokens are used for service attestation
- **CA Keys on Filesystem**: CA private keys are stored in `./ca-keys/` directory
- **⚠️ WARNING**: These are INSECURE and should NEVER be used in production!

### Production Requirements
- **Attestation**: Implement proper attestation validation:
  - Kubernetes Service Account token validation
  - AWS IAM role validation
  - Process ID and parent process validation
  - Unix domain socket ownership validation
- **CA Key Storage**: Use HSM, HashiCorp Vault, or cloud key management
- **Certificate Rotation**: Automatic (already implemented)
- **mTLS**: Fully configured with automatic certificate renewal

## Development Notes

- Services automatically fetch certificates from Workload API on startup
- Certificates are short-lived (1 hour) and automatically rotated at 80% of TTL
- mTLS communication is configured between photo-service and print-service
- Static tokens for local development are configured in `application.yml` and `application-docker.yml`

## Configuration

### Static Attestation Tokens (Local Development)

Configured in each service's `application.yml` or `application-docker.yml`:

```yaml
attestation:
  tokens:
    photo-service: dev-token-photo-service-12345
    print-service: dev-token-print-service-67890
    user-service: dev-token-user-service-abcde
    workload-api-service: dev-token-workload-api-service-fghij
```

### Environment Variables (Docker)

- `ATTESTATION_TOKEN`: Static token for the service (local dev only)
- `WORKLOAD_API_URL`: URL of the Workload API service
- `SPRING_PROFILES_ACTIVE`: Set to `docker` for Dockerized deployment

## Troubleshooting

### Services Not Starting
1. Check Docker logs: `docker-compose logs <service-name>`
2. Verify PostgreSQL is healthy: `docker-compose ps`
3. Check port conflicts: Ensure ports 8080-8083 are available

### Certificate Issues
1. Check Workload API logs: `docker-compose logs workload-api-service`
2. Verify attestation tokens match in configuration
3. Check certificate expiration in logs

### mTLS Communication Issues
1. Verify both services have valid certificates
2. Check mTLS port configuration (8443 for photo-service, 8444 for print-service)
3. Review service logs for SSL handshake errors

## Next Steps

1. ✅ Implement full certificate parsing in `spiffe-common`
2. ✅ Complete mTLS configuration for service-to-service calls
3. ✅ Add static token attestation for local development
4. ⏳ Integrate authentication filters in services
5. ⏳ Add comprehensive error handling and logging
6. ⏳ Implement proper attestation validation for production
