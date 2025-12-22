#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== SPIFFE-like Authentication & Delegation Framework - API Testing ===${NC}\n"

# Step 1: Test Workload API
echo -e "${YELLOW}Step 1: Testing Workload API Health${NC}"
WORKLOAD_HEALTH=$(curl -s http://localhost:8080/workload/v1/health)
echo "Response: $WORKLOAD_HEALTH"
echo ""

# Step 2: Create a test user (we'll need to do this via database or add a registration endpoint)
# For now, let's try to login with a default user or create one
echo -e "${YELLOW}Step 2: Testing User Login${NC}"
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "testpass123"
  }')
echo "Login Response: $LOGIN_RESPONSE"

# Extract user_id if login was successful
USER_ID=$(echo $LOGIN_RESPONSE | grep -o '"user_id":"[^"]*"' | cut -d'"' -f4)

if [ -z "$USER_ID" ]; then
  echo -e "${RED}Login failed. User may not exist. We'll need to create one first.${NC}"
  echo -e "${YELLOW}Note: In a real scenario, you would register a user first.${NC}"
  echo ""
  # For testing, let's assume we have a user ID - we'll use a placeholder
  echo -e "${YELLOW}Using placeholder flow for testing...${NC}"
  USER_ID="00000000-0000-0000-0000-000000000001"
else
  echo -e "${GREEN}Login successful! User ID: $USER_ID${NC}"
fi
echo ""

# Step 3: Issue delegation token for photo service
echo -e "${YELLOW}Step 3: Issuing Delegation Token for Photo Service${NC}"
DELEGATION_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"targetService\": \"photo-service\",
    \"permissions\": [\"read:photos\", \"write:photos\"],
    \"ttlSeconds\": 900
  }")
echo "Delegation Response: $DELEGATION_RESPONSE"

DELEGATION_TOKEN=$(echo $DELEGATION_RESPONSE | grep -o '"delegation_token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$DELEGATION_TOKEN" ]; then
  echo -e "${RED}Failed to get delegation token${NC}"
  exit 1
else
  echo -e "${GREEN}Delegation token obtained!${NC}"
  echo "Token (first 50 chars): ${DELEGATION_TOKEN:0:50}..."
fi
echo ""

# Step 4: Validate delegation token
echo -e "${YELLOW}Step 4: Validating Delegation Token${NC}"
VALIDATION_RESPONSE=$(curl -s "http://localhost:8081/auth/validate?token=$DELEGATION_TOKEN")
echo "Validation Response: $VALIDATION_RESPONSE"
echo ""

# Step 5: Upload a photo
echo -e "${YELLOW}Step 5: Uploading a Photo${NC}"
# Create a simple test image file
echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > /tmp/test_image.png 2>/dev/null || echo "PNG" > /tmp/test_image.png

UPLOAD_RESPONSE=$(curl -s -X POST http://localhost:8082/photos \
  -F "file=@/tmp/test_image.png" \
  -F "userId=$USER_ID")
echo "Upload Response: $UPLOAD_RESPONSE"

PHOTO_ID=$(echo $UPLOAD_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

if [ -z "$PHOTO_ID" ]; then
  echo -e "${RED}Failed to upload photo${NC}"
  exit 1
else
  echo -e "${GREEN}Photo uploaded successfully! Photo ID: $PHOTO_ID${NC}"
fi
echo ""

# Step 6: List user photos
echo -e "${YELLOW}Step 6: Listing User Photos${NC}"
LIST_RESPONSE=$(curl -s "http://localhost:8082/photos/users/$USER_ID")
echo "Photos List: $LIST_RESPONSE"
echo ""

# Step 7: Retrieve a photo
echo -e "${YELLOW}Step 7: Retrieving Photo${NC}"
RETRIEVE_RESPONSE=$(curl -s -o /tmp/retrieved_photo.png -w "\nHTTP Status: %{http_code}\n" "http://localhost:8082/photos/$PHOTO_ID?userId=$USER_ID")
echo "$RETRIEVE_RESPONSE"
if [ -f /tmp/retrieved_photo.png ]; then
  FILE_SIZE=$(stat -f%z /tmp/retrieved_photo.png 2>/dev/null || stat -c%s /tmp/retrieved_photo.png 2>/dev/null)
  echo -e "${GREEN}Photo retrieved! File size: $FILE_SIZE bytes${NC}"
fi
echo ""

# Step 8: Issue delegation token for print service
echo -e "${YELLOW}Step 8: Issuing Delegation Token for Print Service${NC}"
PRINT_DELEGATION_RESPONSE=$(curl -s -X POST http://localhost:8081/auth/delegate \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"targetService\": \"print-service\",
    \"permissions\": [\"read:photos\", \"print:photos\"],
    \"ttlSeconds\": 900
  }")
echo "Print Delegation Response: $PRINT_DELEGATION_RESPONSE"
echo ""

# Step 9: Create a print job
echo -e "${YELLOW}Step 9: Creating Print Job${NC}"
PRINT_JOB_RESPONSE=$(curl -s -X POST http://localhost:8083/print \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"photoIds\": [\"$PHOTO_ID\"]
  }")
echo "Print Job Response: $PRINT_JOB_RESPONSE"

PRINT_JOB_ID=$(echo $PRINT_JOB_RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

if [ -z "$PRINT_JOB_ID" ]; then
  echo -e "${RED}Failed to create print job${NC}"
  exit 1
else
  echo -e "${GREEN}Print job created! Job ID: $PRINT_JOB_ID${NC}"
fi
echo ""

# Step 10: Check print job status
echo -e "${YELLOW}Step 10: Checking Print Job Status${NC}"
sleep 1  # Wait a bit for processing
STATUS_RESPONSE=$(curl -s "http://localhost:8083/print/status/$PRINT_JOB_ID")
echo "Status Response: $STATUS_RESPONSE"
echo ""

# Wait a bit more and check again
echo -e "${YELLOW}Step 11: Checking Print Job Status Again (after processing)${NC}"
sleep 3
STATUS_RESPONSE2=$(curl -s "http://localhost:8083/print/status/$PRINT_JOB_ID")
echo "Status Response: $STATUS_RESPONSE2"
echo ""

echo -e "${GREEN}=== Testing Complete ===${NC}"


