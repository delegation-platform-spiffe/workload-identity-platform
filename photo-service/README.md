# Photo Service

Photo storage and retrieval service.

## Overview

Handles:
- Photo upload (stored as BLOB in PostgreSQL)
- Photo retrieval (with user validation)
- List user photos

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/photo_service
    username: postgres
    password: postgres
```

## Database

Create PostgreSQL database:
```sql
CREATE DATABASE photo_service;
```

Tables are auto-created via JPA (`ddl-auto: update`).

## API Endpoints

All endpoints require authentication via Bearer token in the `Authorization` header.

### POST /photos
Upload a photo.

**Authentication Required**: Valid delegation token with `write:photos` or `read:photos` permission.

**Request Headers**:
```
Authorization: Bearer <delegation_token>
```

**Request Body**: `multipart/form-data`
- `file`: Photo file

**Note**: The `userId` parameter is no longer required - it is automatically extracted from the validated delegation token.

**Response**: Photo entity with ID, metadata, etc.

**Error Responses**:
- `401 Unauthorized`: Missing or invalid Authorization header
- `403 Forbidden`: Insufficient permissions (missing `write:photos` or `read:photos`)

### GET /photos/{photoId}
Retrieve a photo.

**Authentication Required**: Valid delegation token with `read:photos` permission.

**Request Headers**:
```
Authorization: Bearer <delegation_token>
```

**Note**: The `userId` parameter is no longer required - it is automatically extracted from the validated delegation token. Users can only access their own photos.

**Response**: Photo binary data with appropriate content-type headers.

**Error Responses**:
- `401 Unauthorized`: Missing or invalid Authorization header
- `403 Forbidden`: Insufficient permissions or trying to access another user's photo
- `404 Not Found`: Photo not found or doesn't belong to authenticated user

### GET /photos/users/{userId}
List all photos for a user.

**Authentication Required**: Valid delegation token with `read:photos` permission.

**Request Headers**:
```
Authorization: Bearer <delegation_token>
```

**Note**: The `userId` in the path must match the authenticated user from the delegation token.

**Response**: Array of photo metadata.

**Error Responses**:
- `401 Unauthorized`: Missing or invalid Authorization header
- `403 Forbidden`: Insufficient permissions or trying to access another user's photos

## Building and Running

```bash
mvn clean install
mvn spring-boot:run
```

Service will start on port 8082.

## Security

- All `/photos/*` endpoints are protected by `AuthenticationFilter` from `spiffe-common`
- Delegation tokens are validated against the User Service
- User ID is extracted from validated tokens (not from request parameters)
- Permission checks ensure users have required permissions (`read:photos`, `write:photos`)
- Users can only access their own photos




