# SPIFFE-like Authentication & Delegation Framework - Architecture Analysis

## Overview

This document outlines the architecture for a SPIFFE-inspired authentication and delegation framework using Java and Spring Boot, eliminating the need for traditional OAuth2/OIDC client IDs and secrets.

## Core Concepts

### SPIFFE-like Identity Model
- **Workload Identity**: Each service has a unique identity (e.g., `spiffe://example.org/user-service`, `spiffe://example.org/photo-service`)
- **SVID (Service Verifiable Identity Document)**: X.509 certificates or JWTs that prove service identity
- **Trust Domain**: A namespace for identities (e.g., `example.org`)
- **Delegation**: Ability to pass user authorization from one service to another
- **Workload API**: Service endpoint for fetching/refreshing identities

## System Architecture

### 0. Workload API Service (Certificate Authority)
**Purpose**: Centralized certificate issuance and rotation service

**Components**:
- **Certificate Authority**: Root CA and intermediate CAs for signing service certificates
- **Attestation Validator**: Validates service identity before issuing certificates
- **Certificate Issuer**: Issues short-lived X.509 certificates (1 hour TTL)
- **Workload API**: REST API for services to fetch certificates
- **Secure Key Storage**: CA private keys stored in HSM, Vault, or encrypted storage

**Key APIs**:
- `POST /workload/v1/attest` - Service attestation (proves identity)
- `GET /workload/v1/certificates` - Fetch certificate bundle (cert + key + CA chain)
- `GET /workload/v1/health` - Health check

**Database**: None (stateless service, uses secure storage for CA keys)

**SPIFFE Identity**: `spiffe://example.org/workload-api` (self-signed or bootstrap CA)

**Key Principle**: Services never store private keys. They fetch short-lived certificates on-demand, store them only in memory, and refresh them automatically before expiration.

### 1. User Service
**Purpose**: Authentication and delegation management

**Components**:
- **User Authentication API**: Traditional username/password or token-based auth
- **Delegation Token Issuer**: Issues delegation tokens (JWT-based) that allow services to act on behalf of users
- **PostgreSQL Backend**: Stores user credentials, profiles, and delegation policies
- **SPIFFE Identity**: `spiffe://example.org/user-service`

**Key APIs**:
- `POST /auth/login` - User authentication
- `POST /auth/delegate` - Issue delegation token for a specific service
- `GET /auth/validate` - Validate delegation tokens
- `GET /users/{userId}` - User profile management

**Delegation Token Structure**:
```json
{
  "sub": "spiffe://example.org/user-service",
  "aud": "spiffe://example.org/print-service",
  "user_id": "user123",
  "permissions": ["read:photos"],
  "exp": 3600,
  "iat": 1234567890
}
```

### 2. Photo Service
**Purpose**: Photo storage and retrieval

**Components**:
- **Photo Upload API**: Accept photos from authenticated users
- **Photo Storage**: PostgreSQL BLOB storage for photos
- **Delegation Validator**: Validates delegation tokens from print service
- **Service Identity**: `spiffe://example.org/photo-service`
- **mTLS Client**: For machine-to-machine calls to print service

**Key APIs**:
- `POST /photos` - Upload photo (requires user auth)
- `GET /photos/{photoId}` - Retrieve photo (requires user auth or valid delegation)
- `GET /users/{userId}/photos` - List user photos (requires user auth or valid delegation)
- **Internal**: `GET /print-service/status/{printId}` - Check print status (mTLS)

**Database Schema**:
- `photos` table: `id`, `user_id`, `photo_blob`, `metadata`, `created_at`
- `users` table: `id`, `username`, `email` (if needed locally)

### 3. Print Service
**Purpose**: Photo printing with delegated access

**Components**:
- **Print API**: Accepts print requests with delegation tokens
- **Delegation Consumer**: Receives and validates delegation tokens from user service
- **Photo Service Client**: Uses delegation token to fetch photos from photo service
- **Print Status API**: Exposes print status for photo service queries
- **Service Identity**: `spiffe://example.org/print-service`
- **mTLS Server**: Accepts machine-to-machine calls from photo service

**Key APIs**:
- `POST /print` - Print photos (requires delegation token)
- `GET /print/status/{printId}` - Get print status (requires mTLS from photo service)
- **Internal**: Uses delegation token to call `GET /photos/{photoId}` on photo service

## Authentication Flow

### Flow 1: User Delegates to Print Service
```
1. User authenticates with User Service
   → Receives user JWT token

2. User requests delegation for print service
   POST /auth/delegate
   Headers: Authorization: Bearer <user-token>
   Body: { "target_service": "print-service", "permissions": ["read:photos"] }
   → Receives delegation token

3. User calls Print Service with delegation token
   POST /print
   Headers: Authorization: Bearer <delegation-token>
   Body: { "photo_ids": ["photo1", "photo2"] }

4. Print Service validates delegation token with User Service
   GET /auth/validate?token=<delegation-token>
   → Validates token and extracts user_id, permissions

5. Print Service uses delegation token to fetch photos
   GET /photos/{photoId}
   Headers: Authorization: Bearer <delegation-token>
   → Photo Service validates delegation token and returns photo
```

### Flow 2: Photo Service Checks Print Status (Machine-to-Machine)
```
1. Photo Service needs to check print status
   GET /print-service/status/{printId}
   Headers: X-SPIFFE-Identity: spiffe://example.org/photo-service
   mTLS: Uses X.509 certificate for authentication

2. Print Service validates mTLS connection
   → Verifies certificate chain and SPIFFE identity
   → Authorizes based on service identity

3. Print Service returns status
   → 200 OK with print status
```

## Implementation Components

### 1. Workload API Service (Certificate Authority)
**Location**: Separate service (like SPIRE Server)

**Purpose**: Solves the private key storage and certificate rotation problem

**Key Principle**: **Services never store private keys persistently**. They fetch short-lived certificates on-demand from the Workload API.

**Responsibilities**:
- **Certificate Authority (CA)**: Maintains root CA and intermediate CAs
- **Service Attestation**: Validates service identity before issuing certificates
- **Certificate Issuance**: Issues short-lived X.509 certificates (e.g., 1 hour TTL)
- **Certificate Rotation**: Automatically rotates certificates before expiration
- **Private Key Management**: CA private keys stored securely (HSM, Vault, or encrypted storage)

**Workload API Endpoints**:
- `GET /workload/v1/certificates` - Fetch current certificate bundle (cert + key + CA chain)
- `POST /workload/v1/attest` - Service attestation (proves service identity)
- `GET /workload/v1/health` - Health check

**Service Attestation Methods** (choose one or multiple):
1. **Kubernetes Service Account**: Validates K8s service account token
2. **Process ID**: Validates process ID and parent process
3. **Unix Domain Socket**: Validates socket ownership
4. **AWS IAM Role**: Validates EC2 instance IAM role
5. **Static Token**: For development (less secure)

**Certificate Bundle Response**:
```json
{
  "svid": {
    "cert": "-----BEGIN CERTIFICATE-----\n...",
    "key": "-----BEGIN PRIVATE KEY-----\n...",
    "spiffe_id": "spiffe://example.org/photo-service"
  },
  "ca_certs": ["-----BEGIN CERTIFICATE-----\n..."],
  "expires_at": 1234567890,
  "ttl": 3600
}
```

**Security Considerations**:
- CA private keys stored in secure storage (HSM, HashiCorp Vault, AWS Secrets Manager)
- Certificates are short-lived (1 hour) - reduces impact of compromise
- Private keys in response are ephemeral - only valid for certificate lifetime
- TLS between service and Workload API (prevents MITM)

### 2. Service Identity Provider (Client Library)
**Location**: Shared library (`spiffe-common`)

**Purpose**: Client-side library that services use to fetch and manage certificates

**Components**:
- `WorkloadApiClient`: HTTP client for Workload API
- `CertificateManager`: Manages certificate lifecycle
- `CertificateCache`: In-memory cache of current certificate
- `CertificateRefreshScheduler`: Background thread that refreshes certificates before expiration

**How It Works**:
1. **Service Startup**: Service calls Workload API with attestation proof
2. **Certificate Fetch**: Workload API validates attestation and issues certificate bundle
3. **In-Memory Storage**: Certificate and private key stored **only in memory** (never on disk)
4. **Automatic Refresh**: Background thread refreshes certificate at 80% of TTL (e.g., 48 minutes for 1-hour cert)
5. **mTLS Usage**: Certificate used for mTLS connections
6. **Shutdown**: Private key cleared from memory on shutdown

**Key Implementation**:
```java
public class ServiceIdentityProvider {
    private volatile CertificateBundle currentBundle;
    private ScheduledExecutorService refreshScheduler;
    
    public CertificateBundle getCurrentCertificate() {
        if (currentBundle == null || isExpiringSoon(currentBundle)) {
            refreshCertificate();
        }
        return currentBundle;
    }
    
    private void refreshCertificate() {
        // Call Workload API
        CertificateBundle newBundle = workloadApiClient.fetchCertificate(attestationProof);
        
        // Clear old private key from memory
        if (currentBundle != null) {
            clearPrivateKey(currentBundle.getKey());
        }
        
        // Store new bundle in memory only
        currentBundle = newBundle;
        
        // Schedule next refresh at 80% of TTL
        scheduleRefresh(newBundle.getTtl() * 0.8);
    }
}
```

**Benefits**:
- ✅ **No persistent private key storage** - keys only in memory
- ✅ **Automatic rotation** - certificates refreshed before expiration
- ✅ **Short-lived certificates** - reduces attack window
- ✅ **Zero-downtime rotation** - new cert fetched before old one expires

### 2. Delegation Token Handler
**Location**: Shared library

**Components**:
- `DelegationTokenIssuer`: Creates delegation JWTs
- `DelegationTokenValidator`: Validates delegation tokens
- `DelegationTokenFilter`: Spring Web filter for automatic validation
- `DelegationContext`: Thread-local context for authenticated requests

**Token Format**:
- JWT with SPIFFE-like claims
- Signed by User Service private key
- Includes user context and permissions

### 3. mTLS Configuration
**Location**: Shared library

**Components**:
- `MtlsClientConfig`: HTTP client with mTLS
  - Uses `ServiceIdentityProvider` to get current certificate
  - Automatically refreshes certificate when needed
  - Configures SSL context with service certificate and CA trust store
  
- `MtlsServerConfig`: Spring Boot server with mTLS
  - Uses `ServiceIdentityProvider` to get current certificate
  - Configures server SSL context with service certificate
  - Validates client certificates against CA trust store
  - Extracts SPIFFE ID from client certificate for authorization

**Certificate Lifecycle**:
1. Service starts → Fetches certificate from Workload API
2. Certificate stored in memory → Used for mTLS connections
3. Background refresh → New certificate fetched at 80% TTL
4. Old certificate replaced → New certificate used seamlessly
5. Service shuts down → Private key cleared from memory

### 4. Custom Authentication Framework
**Location**: Shared library and each service

**Components**:
- `AuthenticationFilter`: Spring Web filter for delegation token validation
  - Intercepts HTTP requests
  - Extracts `Authorization: Bearer <token>` header
  - Validates delegation tokens via `DelegationTokenValidator`
  - Sets `AuthenticationContext` with user info if valid
  - Returns 401 if invalid/missing token

- `ServiceIdentityFilter`: Spring Web filter for mTLS service identity validation
  - Extracts X.509 certificate from mTLS connection
  - Validates certificate chain and SPIFFE identity
  - Sets `AuthenticationContext` with service identity if valid
  - Returns 401 if certificate invalid

- `AuthenticationContext`: Thread-local context holding authenticated user/service info
  - `getCurrentUser()`: Returns authenticated user ID and permissions
  - `getCurrentService()`: Returns authenticated service identity
  - `isAuthenticated()`: Checks if request is authenticated
  - Cleared after request completion

- `@RequiresAuth` / `@RequiresDelegation` / `@RequiresServiceIdentity`: Custom annotations for endpoint protection
  - Used on controller methods
  - `AuthInterceptor` checks these annotations
  - Throws `UnauthorizedException` if requirements not met

- `AuthInterceptor`: Handler interceptor for method-level authentication checks
  - Implements `HandlerInterceptor`
  - Checks method annotations before handler execution
  - Validates `AuthenticationContext` matches requirements

**Usage Example**:
```java
@RestController
public class PhotoController {
    
    @PostMapping("/photos")
    @RequiresAuth  // Requires user authentication
    public ResponseEntity<Photo> uploadPhoto(@RequestBody PhotoRequest request) {
        String userId = AuthenticationContext.getCurrentUser().getUserId();
        // ... upload logic
    }
    
    @GetMapping("/photos/{photoId}")
    @RequiresDelegation(permissions = {"read:photos"})  // Requires delegation token
    public ResponseEntity<Photo> getPhoto(@PathVariable String photoId) {
        String userId = AuthenticationContext.getCurrentUser().getUserId();
        // ... fetch logic
    }
    
    @GetMapping("/print/status/{printId}")
    @RequiresServiceIdentity(service = "photo-service")  // Requires mTLS from photo-service
    public ResponseEntity<PrintStatus> getStatus(@PathVariable String printId) {
        // ... status check logic
    }
}
```

## Technology Stack

### Core Technologies
- **Java 17+**: Modern Java features
- **Spring Boot 3.x**: Framework for microservices (without Spring Security)
- **Spring Web**: For filters, interceptors, and HTTP handling
- **PostgreSQL**: Database for user data and photo storage
- **JWT (JJWT)**: For delegation tokens
- **BouncyCastle**: For X.509 certificate handling
- **Netty/OkHttp**: For mTLS HTTP clients

### Libraries Needed
- `io.jsonwebtoken:jjwt` - JWT handling
- `org.bouncycastle:bcprov-jdk15on` - Certificate operations
- `org.springframework.boot:spring-boot-starter-web` - Web framework (filters, interceptors)
- `org.postgresql:postgresql` - Database driver
- `org.springframework.boot:spring-boot-starter-webflux` - Reactive HTTP (optional, for async clients)

## Database Schema

### User Service Database
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE delegation_policies (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    target_service VARCHAR(255) NOT NULL,
    permissions TEXT[] NOT NULL,
    expires_in INTEGER DEFAULT 3600,
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Photo Service Database
```sql
CREATE TABLE photos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    photo_blob BYTEA NOT NULL,
    filename VARCHAR(255),
    content_type VARCHAR(100),
    size BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_photos_user_id ON photos(user_id);
```

### Print Service Database
```sql
CREATE TABLE print_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    photo_ids UUID[] NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_print_jobs_user_id ON print_jobs(user_id);
CREATE INDEX idx_print_jobs_status ON print_jobs(status);
```

## Solving Private Key Storage and Certificate Rotation

### The Problem
Traditional mTLS approaches require:
- Services to store private keys persistently (files, databases, secrets managers)
- Manual certificate rotation processes
- Risk of key compromise if storage is breached
- Operational overhead for rotation

### Our Solution: Workload API Pattern

**Key Innovation**: Services **never store private keys persistently**. They fetch short-lived certificates on-demand.

#### How It Works

1. **Service Startup**:
   ```
   Service → Workload API: Attest (prove identity)
   Workload API: Validates attestation proof
   Workload API → Service: Issues certificate bundle (cert + ephemeral key + CA chain)
   Service: Stores certificate bundle **only in memory**
   ```

2. **Certificate Usage**:
   ```
   Service: Uses in-memory certificate for mTLS connections
   Service: Private key never written to disk
   ```

3. **Automatic Rotation** (at 80% of TTL):
   ```
   Background Thread: Detects certificate expiring soon (48 min for 1-hour cert)
   Service → Workload API: Request new certificate
   Workload API → Service: Issues new certificate bundle
   Service: Replaces old certificate in memory
   Service: Clears old private key from memory
   mTLS connections: Seamlessly switch to new certificate
   ```

4. **Service Shutdown**:
   ```
   Service: Clears certificate and private key from memory
   Private key: Never persisted, no cleanup needed
   ```

#### Security Benefits

✅ **No Persistent Key Storage**: Private keys only exist in memory
✅ **Automatic Rotation**: No manual intervention required
✅ **Short-Lived Certificates**: 1-hour TTL reduces attack window
✅ **Zero-Downtime Rotation**: New cert fetched before old one expires
✅ **CA Key Security**: Only CA private keys stored securely (HSM/Vault)

#### CA Private Key Storage Options

The **only** private keys that need persistent storage are the CA keys (used to sign service certificates):

1. **HSM (Hardware Security Module)** - Best Security
   - Tamper-resistant hardware
   - Keys never leave HSM
   - Signing operations performed in HSM
   - Example: AWS CloudHSM, Azure Dedicated HSM

2. **HashiCorp Vault** - Good Security
   - Encrypted key storage
   - Access controls and audit logging
   - Key rotation policies
   - Integration with Workload API

3. **Cloud Key Management** - Good Security
   - AWS KMS / Secrets Manager
   - Azure Key Vault
   - Google Cloud KMS
   - Managed service with encryption

4. **Encrypted File Storage** - Development Only
   - Encrypted files with strong passphrase
   - **Not recommended for production**

#### Certificate Lifecycle Diagram

```
┌─────────────────┐
│  Workload API   │
│  (CA Service)   │
│                 │
│  CA Private Key │
│  (in HSM/Vault) │
└────────┬────────┘
         │
         │ 1. Attest + Issue Certificate
         │    (cert + ephemeral key)
         │
         ▼
┌─────────────────┐
│  Photo Service   │
│                 │
│  Certificate    │
│  Private Key    │ ← Only in memory
│  (in-memory)    │ ← Never on disk
└────────┬────────┘
         │
         │ 2. Use for mTLS
         │
         ▼
┌─────────────────┐
│  Print Service   │
│  (validates)     │
└─────────────────┘

Background: Refresh at 80% TTL
┌─────────────────┐
│  Photo Service   │
│                 │
│  Old Cert: Clear │
│  New Cert: Fetch │
│  (seamless)      │
└─────────────────┘
```

## Security Considerations

### 1. Certificate Management
- **Short-lived certificates**: 1 hour TTL (configurable)
- **Automatic rotation**: Services refresh certificates at 80% of TTL (48 minutes for 1-hour cert)
- **Zero persistent storage**: Private keys only in memory, never on disk
- **Workload API**: Centralized certificate issuance and rotation
- **Certificate revocation**: CRL support or OCSP for compromised certificates
- **CA key security**: CA private keys stored in:
  - **HSM (Hardware Security Module)**: Best security, tamper-resistant
  - **HashiCorp Vault**: Secure key storage with access controls
  - **AWS Secrets Manager / Azure Key Vault**: Cloud-managed key storage
  - **Encrypted file storage**: For development (less secure)

### 2. Delegation Token Security
- Short expiration times (e.g., 15 minutes)
- Scope-limited permissions
- Token revocation capability
- Signed with strong cryptographic keys

### 3. mTLS Security
- Strong cipher suites
- Certificate pinning (optional)
- Mutual authentication required

### 4. Service Identity Verification
- SPIFFE ID validation
- Trust domain verification
- Service-to-service authorization policies

## Project Structure

```
spiffe-like/
├── workload-api-service/
│   ├── src/main/java/
│   │   └── com/example/workloadapi/
│   │       ├── WorkloadApiApplication.java
│   │       ├── controller/
│   │       │   └── WorkloadApiController.java
│   │       ├── service/
│   │       │   ├── CertificateAuthorityService.java
│   │       │   ├── AttestationService.java
│   │       │   └── CertificateIssuanceService.java
│   │       ├── storage/
│   │       │   └── SecureKeyStorage.java  # Interface for HSM/Vault/etc
│   │       └── config/
│   │           └── WebConfig.java
│   └── application.yml
│
├── user-service/
│   ├── src/main/java/
│   │   └── com/example/userservice/
│   │       ├── UserServiceApplication.java
│   │       ├── controller/
│   │       │   ├── AuthController.java
│   │       │   └── UserController.java
│   │       ├── service/
│   │       │   ├── AuthService.java
│   │       │   └── DelegationService.java
│   │       ├── repository/
│   │       │   └── UserRepository.java
│   │       └── config/
│   │           └── WebConfig.java
│   └── application.yml
│
├── photo-service/
│   ├── src/main/java/
│   │   └── com/example/photoservice/
│   │       ├── PhotoServiceApplication.java
│   │       ├── controller/
│   │       │   └── PhotoController.java
│   │       ├── service/
│   │       │   ├── PhotoService.java
│   │       │   └── PrintServiceClient.java
│   │       ├── repository/
│   │       │   └── PhotoRepository.java
│   │       └── config/
│   │           └── WebConfig.java
│   └── application.yml
│
├── print-service/
│   ├── src/main/java/
│   │   └── com/example/printservice/
│   │       ├── PrintServiceApplication.java
│   │       ├── controller/
│   │       │   └── PrintController.java
│   │       ├── service/
│   │       │   ├── PrintService.java
│   │       │   └── PhotoServiceClient.java
│   │       ├── repository/
│   │       │   └── PrintJobRepository.java
│   │       └── config/
│   │           └── WebConfig.java
│   └── application.yml
│
└── spiffe-common/
    ├── src/main/java/
    │   └── com/example/spiffe/
    │       ├── identity/
    │       │   ├── ServiceIdentityProvider.java
    │       │   ├── WorkloadApiClient.java
    │       │   ├── CertificateManager.java
    │       │   ├── CertificateCache.java
    │       │   └── CertificateBundle.java
    │       ├── delegation/
    │       │   ├── DelegationTokenIssuer.java
    │       │   ├── DelegationTokenValidator.java
    │       │   └── DelegationToken.java
    │       ├── mtls/
    │       │   ├── MtlsClientConfig.java
    │       │   └── MtlsServerConfig.java
    │       └── auth/
    │           ├── AuthenticationFilter.java
    │           ├── ServiceIdentityFilter.java
    │           ├── AuthenticationContext.java
    │           ├── AuthInterceptor.java
    │           └── annotations/
    │               ├── RequiresAuth.java
    │               ├── RequiresDelegation.java
    │               └── RequiresServiceIdentity.java
    └── pom.xml
```

## Summary

This architecture provides:

1. **No Static Credentials**: Services authenticate using SPIFFE-like identities (X.509 certs or JWTs)
2. **Delegation Support**: Users can delegate permissions to services via signed tokens
3. **Machine-to-Machine Auth**: Services authenticate to each other using mTLS with SPIFFE identities
4. **Custom Authentication**: Custom filters and interceptors for authentication without Spring Security
5. **Scalable Design**: Each service is independent and can scale horizontally

The system eliminates the need for OAuth2 client secrets by using:
- **Service Identity**: X.509 certificates or service JWTs for service authentication
- **Delegation Tokens**: User-issued JWTs that services can use to act on behalf of users
- **mTLS**: Mutual TLS for secure service-to-service communication

This approach provides better security than static credentials while maintaining simplicity and scalability.

