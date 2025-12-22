# SPIFFE-like Authentication & Delegation Framework - Summary

## Architecture Overview

A SPIFFE-inspired authentication system that eliminates OAuth2/OIDC client secrets by using:
- **Service Identities**: X.509 certificates or JWTs for each service
- **Delegation Tokens**: User-issued JWTs that allow services to act on behalf of users
- **Mutual TLS (mTLS)**: For secure machine-to-machine communication

## Four Services

### 0. Workload API Service (Certificate Authority)
- **Role**: Centralized certificate issuance and rotation
- **Key Innovation**: Services never store private keys persistently
- **How It Works**:
  - Services fetch short-lived certificates (1 hour) on-demand
  - Certificates stored only in memory
  - Automatic rotation at 80% of TTL (48 minutes)
  - CA private keys stored securely (HSM/Vault)
- **SPIFFE Identity**: `spiffe://example.org/workload-api`

### 1. User Service
- **Role**: Authentication and delegation token issuer
- **Database**: PostgreSQL (users, delegation policies)
- **Key Function**: Issues delegation tokens that allow other services to act on behalf of users
- **SPIFFE Identity**: `spiffe://example.org/user-service`

### 2. Photo Service
- **Role**: Photo storage and retrieval
- **Database**: PostgreSQL (photos as BLOB)
- **Key Functions**:
  - Accepts photo uploads from authenticated users
  - Validates delegation tokens from print service
  - Makes mTLS calls to print service to check print status
- **SPIFFE Identity**: `spiffe://example.org/photo-service`

### 3. Print Service
- **Role**: Photo printing with delegated access
- **Database**: PostgreSQL (print jobs)
- **Key Functions**:
  - Accepts print requests with delegation tokens
  - Uses delegation tokens to fetch photos from photo service
  - Exposes print status API (protected by mTLS)
- **SPIFFE Identity**: `spiffe://example.org/print-service`

## Authentication Flows

### User Delegation Flow
```
User → User Service: Authenticate
User → User Service: Request delegation token for print service
User → Print Service: Send print request with delegation token
Print Service → User Service: Validate delegation token
Print Service → Photo Service: Fetch photos using delegation token
Photo Service → User Service: Validate delegation token
Photo Service → Print Service: Return photos
```

### Machine-to-Machine Flow
```
Photo Service → Print Service: Check print status (mTLS)
Print Service: Validates Photo Service certificate
Print Service → Photo Service: Return status
```

## Key Components

### Workload API Service
- **Certificate Authority**: Issues short-lived X.509 certificates
- **Service Attestation**: Validates service identity before issuing certificates
- **Secure Key Storage**: CA private keys in HSM/Vault (only keys that need persistent storage)
- **Workload API**: REST API for services to fetch certificates

### Shared Library (`spiffe-common`)
1. **Service Identity Provider**: Fetches certificates from Workload API, stores only in memory
2. **Certificate Manager**: Automatic rotation at 80% of TTL
3. **Delegation Token Handler**: Issues and validates delegation tokens
4. **mTLS Configuration**: HTTP client/server with mutual TLS using in-memory certificates
5. **Custom Authentication Framework**: Spring Web filters and interceptors for authentication

## Technology Stack
- Java 17+ / Spring Boot 3.x (without Spring Security)
- Spring Web (filters, interceptors)
- PostgreSQL
- JWT (JJWT library)
- BouncyCastle (for certificates)
- Netty/OkHttp (for mTLS)

## Benefits Over Traditional OAuth2
1. **No Static Secrets**: Services use certificates or short-lived JWTs
2. **No Persistent Private Keys**: Private keys only in memory, never on disk
3. **Automatic Rotation**: Certificates automatically refreshed before expiration
4. **Delegation Built-in**: Native support for user-to-service delegation
5. **Service Identity**: Every service has a verifiable identity
6. **Mutual Authentication**: mTLS ensures both parties authenticate
7. **Zero-Downtime Rotation**: New certificates fetched seamlessly before old ones expire

## Security Features
- Short-lived certificates (1 hour TTL)
- Short-lived delegation tokens (15 minutes)
- Certificate revocation support
- Scope-limited permissions
- Trust domain validation

## Project Structure
```
spiffe-like/
├── workload-api-service/  # Certificate authority & issuance
├── user-service/          # Auth & delegation
├── photo-service/         # Photo storage
├── print-service/         # Photo printing
└── spiffe-common/         # Shared SPIFFE components
```

## Private Key Storage Solution

**Problem**: How to store private keys and rotate certificates without persistent key storage?

**Solution**: Workload API Pattern
- Services fetch certificates from Workload API on startup
- Certificates stored **only in memory** (never on disk)
- Automatic refresh at 80% of TTL (48 min for 1-hour cert)
- Only CA private keys need persistent storage (in HSM/Vault)
- Zero-downtime rotation - new cert fetched before old one expires

## Next Steps
1. Implement Workload API Service (certificate authority)
2. Implement `spiffe-common` library with Workload API client
3. Build custom authentication filters and interceptors
4. Build User Service with delegation token issuance
5. Build Photo Service with delegation validation
6. Build Print Service with delegation consumption
7. Configure mTLS between services with automatic certificate rotation

