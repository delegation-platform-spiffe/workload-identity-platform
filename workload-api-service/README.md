# Workload API Service

Certificate Authority and Workload API service for issuing short-lived X.509 certificates to services.

## Overview

This service acts as a Certificate Authority (CA) and provides a Workload API endpoint that services can call to:
1. Attest their identity
2. Receive short-lived certificates (1 hour TTL)
3. Automatically rotate certificates

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

ca:
  key-store:
    path: ./ca-keys  # Directory for CA private keys (local dev)
  trust-domain: example.org
```

## CA Key Storage

For **local development**, CA private keys are stored on the filesystem in `./ca-keys/`:
- `ca-private-key.pem` - CA private key
- `ca-certificate.pem` - CA certificate

**⚠️ WARNING**: Filesystem storage is for development only. In production, use:
- HSM (Hardware Security Module)
- HashiCorp Vault
- AWS Secrets Manager / Azure Key Vault
- Google Cloud KMS

## API Endpoints

### POST /workload/v1/attest
Attest service identity and receive an attestation token.

**Request**:
```json
{
  "service_name": "photo-service",
  "attestation_proof": {
    "process_id": "12345"
  }
}
```

**Response**:
```json
{
  "token": "attestation-token-uuid"
}
```

### GET /workload/v1/certificates
Fetch certificate bundle using attestation token.

**Headers**:
```
Authorization: Bearer <attestation-token>
```

**Response**:
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

### GET /workload/v1/health
Health check endpoint.

## Building and Running

```bash
mvn clean install
mvn spring-boot:run
```

Service will start on port 8080.

## Implementation Status

- ✅ CA key pair generation
- ✅ Certificate issuance
- ✅ Basic attestation (simplified for local dev)
- ⚠️ CA key persistence (placeholder - needs PEM file I/O)
- ⚠️ Certificate bundle parsing (needs PEM parsing implementation)




