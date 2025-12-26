# SPIFFE Common Library

Shared library containing common SPIFFE-like authentication and delegation components.

## Components

### Identity Management
- `CertificateBundle`: Represents a certificate bundle with cert, key, and CA chain
- `WorkloadApiClient`: Client for communicating with Workload API
- `ServiceIdentityProvider`: Manages service identity certificates with automatic rotation

### Authentication
- `AuthenticationContext`: Thread-local context for authenticated requests
- `AuthenticationFilter`: Spring Web filter for delegation token validation

### Delegation
- `DelegationToken`: Represents a delegation token
- `DelegationTokenIssuer`: Issues delegation JWTs
- `DelegationTokenValidator`: Validates delegation tokens

### mTLS
- `MtlsClientConfig`: Configures mTLS client for service-to-service communication
- `MtlsServerConfig`: Configures mTLS server
- `SpiffeIdentityExtractor`: Extracts SPIFFE identity from certificates

## Usage

This library is intended to be used as a dependency in other services:

```xml
<dependency>
    <groupId>com.example.spiffe</groupId>
    <artifactId>spiffe-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Building

```bash
mvn clean install
```

This will install the library to your local Maven repository.

## Implementation Status

- ✅ Basic structure and interfaces
- ✅ Certificate parsing from PEM
- ✅ mTLS client/server configuration
- ✅ Service identity filter
- ✅ Delegation token validation
- ✅ Authentication context management
