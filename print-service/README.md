# Print Service

Photo printing service with delegation support.

## Overview

Handles:
- Print job creation (with delegation tokens)
- Print job status checking (via mTLS from photo service)
- Photo fetching from photo service using delegation tokens

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/print_service
    username: postgres
    password: postgres
```

## Database

Create PostgreSQL database:
```sql
CREATE DATABASE print_service;
```

Tables are auto-created via JPA (`ddl-auto: update`).

## API Endpoints

All endpoints require authentication via Bearer token in the `Authorization` header.

### POST /print
Create a print job.

**Authentication Required**: Valid delegation token with `print:photos` or `read:photos` permission.

**Request Headers**:
```
Authorization: Bearer <delegation_token>
```

**Request Body**:
```json
{
  "photoIds": ["photo-uuid-1", "photo-uuid-2"]
}
```

**Note**: The `userId` parameter is optional. If provided, it must match the authenticated user from the token. If omitted, the user ID from the authentication token will be used.

**Response**: Print job entity with status.

**Error Responses**:
- `401 Unauthorized`: Missing or invalid Authorization header
- `403 Forbidden`: Insufficient permissions or trying to create print job for another user

### GET /print/status/{printId}
Get print job status.

**Authentication Required**: Valid delegation token with `read:photos` or `print:photos` permission.

**Request Headers**:
```
Authorization: Bearer <delegation_token>
```

**Note**: Users can only access their own print jobs.

**Response**:
```json
{
  "id": "job-uuid",
  "userId": "user-uuid",
  "photoIds": ["photo-uuid-1"],
  "status": "COMPLETED",
  "createdAt": "2024-01-01T00:00:00Z",
  "completedAt": "2024-01-01T00:00:02Z"
}
```

**Error Responses**:
- `401 Unauthorized`: Missing or invalid Authorization header
- `403 Forbidden`: Insufficient permissions or trying to access another user's print job
- `404 Not Found`: Print job not found or doesn't belong to authenticated user

## Building and Running

```bash
mvn clean install
mvn spring-boot:run
```

Service will start on port 8083.

## Security

- All `/print/*` endpoints are protected by `AuthenticationFilter` from `spiffe-common`
- Delegation tokens are validated against the User Service
- User ID is extracted from validated tokens (not from request parameters)
- Permission checks ensure users have required permissions (`read:photos`, `print:photos`)
- Users can only access their own print jobs




