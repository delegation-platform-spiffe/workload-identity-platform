# User Service

Authentication and delegation token service.

## Overview

Handles:
- User authentication (username/password)
- Delegation token issuance (JWTs that allow services to act on behalf of users)
- Delegation token validation

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/user_service
    username: postgres
    password: postgres

delegation:
  signing-key: <change-this-to-secure-random-key-min-256-bits>
  issuer-spiffe-id: spiffe://example.org/user-service
  default-ttl: 900  # 15 minutes
```

## Database

Create PostgreSQL database:
```sql
CREATE DATABASE user_service;
```

Tables are auto-created via JPA (`ddl-auto: update`).

## API Endpoints

### POST /auth/login
User login.

**Request**:
```json
{
  "username": "alice",
  "password": "password123"
}
```

**Response**:
```json
{
  "user_id": "uuid",
  "username": "alice",
  "access_token": "jwt-user-token",
  "token_type": "Bearer",
  "message": "Login successful"
}
```

### POST /auth/delegate
Issue delegation token for a service.

**Authentication Required**: This endpoint requires a valid user authentication token in the `Authorization` header.

**Request Headers**:
```
Authorization: Bearer <access_token_from_login>
```

**Request Body**:
```json
{
  "userId": "user-uuid",
  "targetService": "print-service",
  "permissions": ["read:photos"],
  "ttlSeconds": 900
}
```

**Note**: The `userId` in the request body is optional. If provided, it must match the authenticated user from the token. If omitted, the user ID from the authentication token will be used. **Important**: JSON does not support comments - do not include `#` or `//` comments in your JSON request body.

**Response**:
```json
{
  "delegation_token": "jwt-token",
  "expires_in": 900
}
```

### POST /auth/validate
Validate a delegation token.

**Request Body**:
```json
{
  "token": "delegation-token-jwt"
}
```

**Response**:
```json
{
  "valid": true,
  "token": {
    "user_id": "uuid",
    "permissions": ["read:photos"],
    "audience": "spiffe://example.org/print-service",
    "expires_at": 1234567890
  }
}
```

**Note**: Uses POST instead of GET to avoid exposing tokens in query parameters (security best practice).

## Building and Running

```bash
mvn clean install
mvn spring-boot:run
```

Service will start on port 8081.

## Security Notes

- Change `delegation.signing-key` to a secure random key (minimum 256 bits)
- Use strong password hashing (BCrypt is used)
- In production, add rate limiting and proper authentication validation




