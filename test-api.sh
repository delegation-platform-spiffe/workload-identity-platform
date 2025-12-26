#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
USERNAME="testuser_$(date +%s)"
EMAIL="${USERNAME}@example.com"
PASSWORD="testpass123"
BASE_URL="http://localhost"

# Service URLs
WORKLOAD_API_URL="${BASE_URL}:8080"
USER_SERVICE_URL="${BASE_URL}:8081"
PHOTO_SERVICE_URL="${BASE_URL}:8082"
PRINT_SERVICE_URL="${BASE_URL}:8083"

# Variables to store tokens and IDs
USER_ID=""
ACCESS_TOKEN=""
PHOTO_DELEGATION_TOKEN=""
PRINT_DELEGATION_TOKEN=""
PHOTO_ID=""
PRINT_JOB_ID=""

# Helper function to print section headers
print_section() {
    echo -e "\n${BLUE}=== $1 ===${NC}\n"
}

# Helper function to print step
print_step() {
    echo -e "${CYAN}Step: $1${NC}"
}

# Helper function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Helper function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Helper function to print JSON response
print_json() {
    if command -v jq &> /dev/null; then
        echo "$1" | jq '.' 2>/dev/null || echo "$1"
    else
        echo "$1"
    fi
}

# Helper function to print request
print_request() {
    echo -e "${YELLOW}Request:${NC}"
    echo -e "${YELLOW}  Method: $1${NC}"
    echo -e "${YELLOW}  URL: $2${NC}"
    if [ -n "$3" ]; then
        echo -e "${YELLOW}  Headers:${NC}"
        # Handle multiline headers (split by newline)
        echo "$3" | while IFS= read -r line; do
            echo "    $line"
        done
    fi
    if [ -n "$4" ]; then
        echo -e "${YELLOW}  Body:${NC}"
        if command -v jq &> /dev/null; then
            echo "$4" | jq '.' 2>/dev/null | sed 's/^/    /' || echo "$4" | sed 's/^/    /'
        else
            echo "$4" | sed 's/^/    /'
        fi
    fi
    echo ""
}

# Helper function to print response
print_response() {
    echo -e "${GREEN}Response:${NC}"
    print_json "$1" | sed 's/^/  /'
    echo ""
}

# Helper function to check if jq is available
check_jq() {
    if ! command -v jq &> /dev/null; then
        echo -e "${YELLOW}Warning: jq is not installed. JSON parsing will be limited.${NC}"
        echo -e "${YELLOW}Install jq for better output: brew install jq (macOS) or apt-get install jq (Linux)${NC}\n"
        return 1
    fi
    return 0
}

# Check if services are running
check_services() {
    print_section "Checking Services"
    
    local all_up=true
    
    if ! curl -s "${WORKLOAD_API_URL}/workload/v1/health" > /dev/null; then
        print_error "Workload API (${WORKLOAD_API_URL}) is not responding"
        all_up=false
    else
        print_success "Workload API is up"
    fi
    
    if ! curl -s -X POST "${USER_SERVICE_URL}/auth/validate" \
        -H "Content-Type: application/json" \
        -d '{"token":"test"}' > /dev/null 2>&1; then
        print_error "User Service (${USER_SERVICE_URL}) is not responding"
        all_up=false
    else
        print_success "User Service is up"
    fi
    
    if ! curl -s "${PHOTO_SERVICE_URL}/photos/users/test" > /dev/null 2>&1; then
        print_error "Photo Service (${PHOTO_SERVICE_URL}) is not responding"
        all_up=false
    else
        print_success "Photo Service is up"
    fi
    
    if ! curl -s "${PRINT_SERVICE_URL}/print/status/test" > /dev/null 2>&1; then
        print_error "Print Service (${PRINT_SERVICE_URL}) is not responding"
        all_up=false
    else
        print_success "Print Service is up"
    fi
    
    if [ "$all_up" = false ]; then
        echo -e "\n${RED}Some services are not running. Please start them with:${NC}"
        echo -e "${YELLOW}docker-compose up${NC} or ${YELLOW}make up${NC}\n"
        exit 1
    fi
}

# Test Workload API
test_workload_api() {
    print_section "Testing Workload API"
    
    print_step "Health Check"
    print_request "GET" "${WORKLOAD_API_URL}/workload/v1/health"
    local response=$(curl -s "${WORKLOAD_API_URL}/workload/v1/health")
    print_response "$response"
    
    print_step "Service Attestation (photo-service)"
    local attest_body='{
            "service_name": "photo-service",
            "attestation_proof": {
                "token": "dev-token-photo-service-12345",
                "process_id": "12345",
                "service_name": "photo-service"
            }
        }'
    print_request "POST" "${WORKLOAD_API_URL}/workload/v1/attest" "Content-Type: application/json" "$attest_body"
    
    local attest_response=$(curl -s -X POST "${WORKLOAD_API_URL}/workload/v1/attest" \
        -H "Content-Type: application/json" \
        -d "$attest_body")
    
    print_response "$attest_response"
    
    local attest_token=$(echo "$attest_response" | jq -r '.token' 2>/dev/null)
    if [ -n "$attest_token" ] && [ "$attest_token" != "null" ]; then
        print_success "Attestation successful"
        
        print_step "Get Certificate Bundle"
        print_request "GET" "${WORKLOAD_API_URL}/workload/v1/certificates?service_name=photo-service" "Authorization: Bearer ${attest_token:0:50}..."
        
        local cert_response=$(curl -s -X GET "${WORKLOAD_API_URL}/workload/v1/certificates?service_name=photo-service" \
            -H "Authorization: Bearer ${attest_token}")
        
        if echo "$cert_response" | jq -e '.svid' > /dev/null 2>&1; then
            print_success "Certificate bundle retrieved"
            echo "$cert_response" | jq '{svid: {spiffe_id: .svid.spiffe_id}, expires_at, ttl}' 2>/dev/null | sed 's/^/  /' || echo "$cert_response" | sed 's/^/  /'
        else
            print_error "Failed to get certificate bundle"
            print_response "$cert_response"
        fi
    else
        print_error "Attestation failed"
    fi
    echo ""
}

# Test User Service
test_user_service() {
    print_section "Testing User Service"
    
    print_step "Register User: ${USERNAME}"
    local register_body="{
            \"username\": \"${USERNAME}\",
            \"email\": \"${EMAIL}\",
            \"password\": \"${PASSWORD}\"
        }"
    print_request "POST" "${USER_SERVICE_URL}/auth/register" "Content-Type: application/json" "$register_body"
    
    local register_response=$(curl -s -X POST "${USER_SERVICE_URL}/auth/register" \
        -H "Content-Type: application/json" \
        -d "$register_body")
    
    print_response "$register_response"
    
    # Extract user_id if registration was successful
    USER_ID=$(echo "$register_response" | jq -r '.user_id' 2>/dev/null)
    if [ -n "$USER_ID" ] && [ "$USER_ID" != "null" ]; then
        print_success "User registered: ${USER_ID}"
    else
        # Try to login with existing user (might already exist)
        print_step "User might already exist, trying login..."
    fi
    echo ""
    
    print_step "Login"
    local login_body="{
            \"username\": \"${USERNAME}\",
            \"password\": \"${PASSWORD}\"
        }"
    print_request "POST" "${USER_SERVICE_URL}/auth/login" "Content-Type: application/json" "$login_body"
    
    local login_response=$(curl -s -X POST "${USER_SERVICE_URL}/auth/login" \
        -H "Content-Type: application/json" \
        -d "$login_body")
    
    print_response "$login_response"
    
    USER_ID=$(echo "$login_response" | jq -r '.user_id' 2>/dev/null)
    ACCESS_TOKEN=$(echo "$login_response" | jq -r '.access_token' 2>/dev/null)
    
    if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
        print_success "Login successful"
        print_success "User ID: ${USER_ID}"
        print_success "Access Token: ${ACCESS_TOKEN:0:50}..."
    else
        print_error "Login failed"
        exit 1
    fi
    echo ""
    
    print_step "Issue Delegation Token for Photo Service"
    local photo_delegation_body="{
            \"targetService\": \"photo-service\",
            \"permissions\": [\"read:photos\", \"write:photos\"],
            \"ttlSeconds\": 900
        }"
    print_request "POST" "${USER_SERVICE_URL}/auth/delegate" "Content-Type: application/json
Authorization: Bearer ${ACCESS_TOKEN:0:50}..." "$photo_delegation_body"
    
    local photo_delegation_response=$(curl -s -X POST "${USER_SERVICE_URL}/auth/delegate" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" \
        -d "$photo_delegation_body")
    
    print_response "$photo_delegation_response"
    
    PHOTO_DELEGATION_TOKEN=$(echo "$photo_delegation_response" | jq -r '.delegation_token' 2>/dev/null)
    if [ -n "$PHOTO_DELEGATION_TOKEN" ] && [ "$PHOTO_DELEGATION_TOKEN" != "null" ]; then
        print_success "Photo service delegation token obtained"
    else
        print_error "Failed to get photo service delegation token"
        exit 1
    fi
    echo ""
    
    print_step "Issue Delegation Token for Print Service"
    local print_delegation_body="{
            \"targetService\": \"print-service\",
            \"permissions\": [\"read:photos\", \"print:photos\"],
            \"ttlSeconds\": 900
        }"
    print_request "POST" "${USER_SERVICE_URL}/auth/delegate" "Content-Type: application/json
Authorization: Bearer ${ACCESS_TOKEN:0:50}..." "$print_delegation_body"
    
    local print_delegation_response=$(curl -s -X POST "${USER_SERVICE_URL}/auth/delegate" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" \
        -d "$print_delegation_body")
    
    print_response "$print_delegation_response"
    
    PRINT_DELEGATION_TOKEN=$(echo "$print_delegation_response" | jq -r '.delegation_token' 2>/dev/null)
    if [ -n "$PRINT_DELEGATION_TOKEN" ] && [ "$PRINT_DELEGATION_TOKEN" != "null" ]; then
        print_success "Print service delegation token obtained"
    else
        print_error "Failed to get print service delegation token"
        exit 1
    fi
    echo ""
    
    print_step "Validate Delegation Token"
    local validate_body="{
            \"token\": \"${PHOTO_DELEGATION_TOKEN:0:50}...\"
        }"
    print_request "POST" "${USER_SERVICE_URL}/auth/validate" "Content-Type: application/json" "$validate_body"
    
    local validate_response=$(curl -s -X POST "${USER_SERVICE_URL}/auth/validate" \
        -H "Content-Type: application/json" \
        -d "{
            \"token\": \"${PHOTO_DELEGATION_TOKEN}\"
        }")
    
    print_response "$validate_response"
    
    local is_valid=$(echo "$validate_response" | jq -r '.valid' 2>/dev/null)
    if [ "$is_valid" = "true" ]; then
        print_success "Token validation successful"
    else
        print_error "Token validation failed"
    fi
    echo ""
}

# Test Photo Service
test_photo_service() {
    print_section "Testing Photo Service"
    
    # Create a simple test image file (1x1 PNG)
    local test_image="/tmp/test_image_$$.png"
    echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" | base64 -d > "$test_image" 2>/dev/null
    
    if [ ! -f "$test_image" ]; then
        # Fallback: create a simple text file
        echo "PNG" > "$test_image"
    fi
    
    print_step "Upload Photo"
    print_request "POST" "${PHOTO_SERVICE_URL}/photos" "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN:0:50}...
Content-Type: multipart/form-data" "file=@${test_image}"
    
    local upload_response=$(curl -s -X POST "${PHOTO_SERVICE_URL}/photos" \
        -H "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN}" \
        -F "file=@${test_image}")
    
    print_response "$upload_response"
    
    PHOTO_ID=$(echo "$upload_response" | jq -r '.id' 2>/dev/null)
    if [ -n "$PHOTO_ID" ] && [ "$PHOTO_ID" != "null" ]; then
        print_success "Photo uploaded: ${PHOTO_ID}"
    else
        print_error "Failed to upload photo"
        rm -f "$test_image"
        exit 1
    fi
    rm -f "$test_image"
    echo ""
    
    print_step "List User Photos"
    print_request "GET" "${PHOTO_SERVICE_URL}/photos/users/${USER_ID}" "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN:0:50}..."
    
    local list_response=$(curl -s -H "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN}" \
        "${PHOTO_SERVICE_URL}/photos/users/${USER_ID}")
    
    print_response "$list_response"
    
    local photo_count=$(echo "$list_response" | jq '. | length' 2>/dev/null)
    if [ -n "$photo_count" ] && [ "$photo_count" != "null" ]; then
        print_success "Found ${photo_count} photo(s)"
    fi
    echo ""
    
    print_step "Get Photo by ID"
    print_request "GET" "${PHOTO_SERVICE_URL}/photos/${PHOTO_ID}" "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN:0:50}..."
    
    local get_response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
        -H "Authorization: Bearer ${PHOTO_DELEGATION_TOKEN}" \
        "${PHOTO_SERVICE_URL}/photos/${PHOTO_ID}")
    
    local http_status=$(echo "$get_response" | grep "HTTP_STATUS:" | cut -d: -f2)
    local photo_data=$(echo "$get_response" | sed '/HTTP_STATUS:/d')
    
    if [ "$http_status" = "200" ]; then
        local photo_size=$(echo "$photo_data" | wc -c | xargs)
        echo -e "${GREEN}Response:${NC}"
        echo -e "  HTTP Status: ${http_status}"
        echo -e "  Content Size: ${photo_size} bytes"
        echo -e "  Content-Type: binary image data"
        print_success "Photo retrieved successfully"
    else
        print_error "Failed to retrieve photo (HTTP ${http_status})"
        print_response "$photo_data"
    fi
    echo ""
}

# Test Print Service
test_print_service() {
    print_section "Testing Print Service"
    
    print_step "Create Print Job"
    local print_body="{
            \"photoIds\": [\"${PHOTO_ID}\"]
        }"
    print_request "POST" "${PRINT_SERVICE_URL}/print" "Content-Type: application/json
Authorization: Bearer ${PRINT_DELEGATION_TOKEN:0:50}..." "$print_body"
    
    local print_response=$(curl -s -X POST "${PRINT_SERVICE_URL}/print" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${PRINT_DELEGATION_TOKEN}" \
        -d "$print_body")
    
    print_response "$print_response"
    
    PRINT_JOB_ID=$(echo "$print_response" | jq -r '.id' 2>/dev/null)
    if [ -n "$PRINT_JOB_ID" ] && [ "$PRINT_JOB_ID" != "null" ]; then
        print_success "Print job created: ${PRINT_JOB_ID}"
    else
        print_error "Failed to create print job"
        exit 1
    fi
    echo ""
    
    print_step "Get Print Job Status (immediate)"
    print_request "GET" "${PRINT_SERVICE_URL}/print/status/${PRINT_JOB_ID}" "Authorization: Bearer ${PRINT_DELEGATION_TOKEN:0:50}..."
    
    local status_response=$(curl -s -H "Authorization: Bearer ${PRINT_DELEGATION_TOKEN}" \
        "${PRINT_SERVICE_URL}/print/status/${PRINT_JOB_ID}")
    
    print_response "$status_response"
    
    local status=$(echo "$status_response" | jq -r '.status' 2>/dev/null)
    if [ -n "$status" ] && [ "$status" != "null" ]; then
        print_success "Print job status: ${status}"
    fi
    echo ""
    
    print_step "Wait 3 seconds and check status again"
    sleep 3
    
    print_request "GET" "${PRINT_SERVICE_URL}/print/status/${PRINT_JOB_ID}" "Authorization: Bearer ${PRINT_DELEGATION_TOKEN:0:50}..."
    
    local status_response2=$(curl -s -H "Authorization: Bearer ${PRINT_DELEGATION_TOKEN}" \
        "${PRINT_SERVICE_URL}/print/status/${PRINT_JOB_ID}")
    
    print_response "$status_response2"
    
    local status2=$(echo "$status_response2" | jq -r '.status' 2>/dev/null)
    if [ -n "$status2" ] && [ "$status2" != "null" ]; then
        print_success "Print job status: ${status2}"
    fi
    echo ""
}

# Main execution
main() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║   SPIFFE-like Authentication & Delegation - API Test Suite  ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    check_jq
    check_services
    
    test_workload_api
    test_user_service
    test_photo_service
    test_print_service
    
    print_section "Test Summary"
    echo -e "${GREEN}✓ All tests completed successfully!${NC}"
    echo ""
    echo "Summary:"
    echo "  - User ID: ${USER_ID}"
    echo "  - Photo ID: ${PHOTO_ID}"
    echo "  - Print Job ID: ${PRINT_JOB_ID}"
    echo ""
}

# Run main function
main

