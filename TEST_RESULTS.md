# API Testing Results

## Test Summary

### ✅ Successful Tests

1. **Workload API Service** (Port 8080)
   - Health check: ✅ `GET /workload/v1/health` returns `{"status":"healthy"}`

2. **User Service** (Port 8081)
   - User Registration: ✅ `POST /auth/register` - Successfully created user
   - User Login: ✅ `POST /auth/login` - Successfully authenticated
   - Delegation Token Issuance: ✅ `POST /auth/delegate` - Successfully issued JWT tokens
   - Token Validation: ✅ `GET /auth/validate?token=...` - Successfully validated tokens

### ⚠️ Issues Found

1. **Photo Service** (Port 8082)
   - Photo Upload: ❌ `POST /photos` - Failing with database column mapping error
   - Error: `column "photo_blob" is of type bytea but expression is of type bigint`
   - Root Cause: Hibernate column mapping issue with `@Lob` and `byte[]` type
   - Status: Needs fix for column mapping

2. **Print Service** (Port 8083)
   - Not yet tested (blocked by photo upload issue)

## Test Commands Used

### 1. Register User
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"testpass123"}'
```

### 2. Login
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass123"}'
```

### 3. Issue Delegation Token
```bash
curl -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER_ID",
    "targetService": "photo-service",
    "permissions": ["read:photos", "write:photos"],
    "ttlSeconds": 900
  }'
```

### 4. Validate Token
```bash
curl "http://localhost:8081/auth/validate?token=DELEGATION_TOKEN"
```

## Next Steps

1. Fix photo upload column mapping issue
2. Test photo upload after fix
3. Test photo retrieval
4. Test print job creation
5. Test print status checking


