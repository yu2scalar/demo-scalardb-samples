#!/bin/bash

# Smart API Testing Script - Automatically discovers schema and tests endpoints
# Usage: ./test-api-smart.sh <entity_name> [base_url]    - Test a specific entity
#        ./test-api-smart.sh <base_url>                  - Auto-discover and test all entities
#        ./test-api-smart.sh                             - Show help
# Example: ./test-api-smart.sh TestP1 http://localhost:8080
#          ./test-api-smart.sh http://localhost:8080

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
ENTITY_NAME="${1:-}"
BASE_URL="${2:-http://localhost:8080}"
CONTENT_TYPE="Content-Type: application/json"

# If first argument looks like a URL, treat as base_url with auto-discovery mode
if [[ "$ENTITY_NAME" =~ ^https?:// ]]; then
    BASE_URL="$ENTITY_NAME"
    ENTITY_NAME=""
fi

# Test results tracking
declare -A TEST_RESULTS
RESULTS_FILE="/tmp/smart_test_results_$$"
echo "" > "$RESULTS_FILE"

# Function to record test result
record_result() {
    local endpoint="$1"
    local operation="$2"
    local status="$3"  # OK or NG
    echo "${endpoint}|${operation}|${status}" >> "$RESULTS_FILE"
}

# Function to print colored output
print_step() {
    echo -e "${BLUE}==> $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
    echo "✗ $1" >> /tmp/smart_test_log 2>/dev/null || true
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

print_response() {
    echo -e "${YELLOW}Response:${NC}"
    echo "$1" | jq '.' 2>/dev/null || echo "$1"
}

print_header() {
    echo -e "${BLUE}=========================================="
    echo -e "$1"
    echo -e "==========================================${NC}"
}

# Function to discover all entities from OpenAPI docs
discover_entities() {
    local url="$1"
    local api_docs=$(curl -s "${url}/v3/api-docs" 2>/dev/null)

    if [ -z "$api_docs" ] || [ "$api_docs" = "null" ]; then
        print_error "Could not fetch API documentation from ${url}/v3/api-docs"
        exit 1
    fi

    # Extract entity names from DTO schemas (e.g., "NsMysqlAllTypeTestDto" -> "NsMysqlAllTypeTest")
    echo "$api_docs" | jq -r '.components.schemas | keys[]' 2>/dev/null | \
        grep -E 'Dto$' | \
        grep -v '^ApiResponse' | \
        grep -v '^SqlCommandDto$' | \
        sed 's/Dto$//' | \
        sort -u
}

# Function to convert entity name to different cases
to_kebab_case() {
    echo "$1" | sed 's/\([A-Z]\)/-\1/g' | sed 's/^-//' | tr '[:upper:]' '[:lower:]'
}

# Get script directory for calling other scripts (needed early for auto-discovery)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if entity name is provided
if [ -z "$ENTITY_NAME" ]; then
    if [ -z "$1" ]; then
        # No arguments at all - show usage
        echo "Usage: $0 <entity_name> [base_url]    - Test a specific entity"
        echo "       $0 <base_url>                  - Auto-discover and test all entities"
        echo "       $0                             - Show this help"
        echo ""
        echo "Example: $0 TestAllTypeTest http://localhost:8080"
        echo "         $0 http://localhost:8080"
        echo ""
        echo "Features:"
        echo "  • Automatic schema discovery using get-schema.sh"
        echo "  • Intelligent data format generation"
        echo "  • Complete CRUD cycle testing"
        echo "  • Automatic diagnostic analysis on failures"
        echo "  • Composite primary key detection"
        echo "  • Auto-discovery mode: test all entities when no entity specified"
        echo ""
        echo "Environment variables:"
        echo "  VERBOSE=true    - Run diagnostic verification even on success"
        echo ""
        echo "Related scripts:"
        echo "  ./get-schema.sh <entity> [url]     - Detailed schema analysis"
        echo "  ./diagnose-api.sh <entity> [url]   - API troubleshooting"
        echo "  ./test-api-working.sh <entity> [url] - Pre-configured working tests"
        echo ""
        exit 1
    fi

    # Auto-discovery mode
    print_header "Auto-Discovery Mode"
    print_info "No entity specified. Discovering all entities from ${BASE_URL}..."
    echo ""

    ENTITIES=$(discover_entities "$BASE_URL")

    if [ -z "$ENTITIES" ]; then
        print_error "No entities found"
        exit 1
    fi

    ENTITY_COUNT=$(echo "$ENTITIES" | wc -l)
    print_info "Found ${ENTITY_COUNT} entities:"
    echo "$ENTITIES" | while read entity; do echo "  • $entity"; done
    echo ""

    # Run test-api-smart.sh for each entity (recursive call)
    DISCOVERY_PASS=0
    DISCOVERY_FAIL=0
    for entity in $ENTITIES; do
        echo ""
        echo "############################################"
        echo "# Testing Entity: $entity"
        echo "############################################"
        if "${SCRIPT_DIR}/test-api-smart.sh" "$entity" "$BASE_URL"; then
            DISCOVERY_PASS=$((DISCOVERY_PASS + 1))
        else
            DISCOVERY_FAIL=$((DISCOVERY_FAIL + 1))
        fi
    done

    echo ""
    print_header "Auto-Discovery Summary"
    echo -e "  ${GREEN}Passed: ${DISCOVERY_PASS}${NC}"
    echo -e "  ${RED}Failed: ${DISCOVERY_FAIL}${NC}"
    echo -e "  Total:  $((DISCOVERY_PASS + DISCOVERY_FAIL))"
    echo ""

    exit 0
fi

# Convert entity name to appropriate formats
ENTITY_KEBAB=$(to_kebab_case "$ENTITY_NAME")
API_PATH="${BASE_URL}/${ENTITY_KEBAB}"

echo "=========================================="
echo "Smart API Testing for: ${ENTITY_NAME}"
echo "Base URL: ${BASE_URL}"
echo "API Path: ${API_PATH}"
echo "=========================================="
echo ""

# Initialize test log
rm -f /tmp/smart_test_log 2>/dev/null || true

# Step 0: Run schema discovery first
print_step "Running schema discovery analysis"

if [ -f "${SCRIPT_DIR}/get-schema.sh" ]; then
    print_info "Getting detailed schema information..."
    echo ""
    echo "--- Schema Discovery Output ---"
    "${SCRIPT_DIR}/get-schema.sh" "${ENTITY_NAME}" "${BASE_URL}" | tail -30
    echo "--- End Schema Discovery ---"
    echo ""
else
    print_info "get-schema.sh not found, proceeding with basic discovery"
fi

# Step 1: Discover schema from OpenAPI/Swagger
print_step "Discovering ${ENTITY_NAME} schema from API documentation"

API_DOCS=$(curl -s "${BASE_URL}/v3/api-docs" 2>/dev/null)

if [ -z "$API_DOCS" ] || [ "$API_DOCS" = "null" ]; then
    print_error "Could not fetch API documentation"
    echo "Make sure your Spring Boot application is running with Swagger enabled"
    exit 1
fi

# Extract schema for the entity's DTO
SCHEMA=$(echo "$API_DOCS" | jq -r --arg entity "${ENTITY_NAME}Dto" '.components.schemas[$entity]' 2>/dev/null)

if [ "$SCHEMA" = "null" ] || [ -z "$SCHEMA" ]; then
    print_error "Could not find schema for ${ENTITY_NAME}Dto"
    print_info "Available schemas:"
    echo "$API_DOCS" | jq -r '.components.schemas | keys[]' 2>/dev/null | grep -i "${ENTITY_NAME}" || true
    echo ""
    print_info "Trying to find schema by searching for similar names..."
    
    # Try to find schema with case-insensitive search
    POSSIBLE_SCHEMAS=$(echo "$API_DOCS" | jq -r '.components.schemas | keys[]' 2>/dev/null | grep -i "$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')" || true)
    
    if [ ! -z "$POSSIBLE_SCHEMAS" ]; then
        SCHEMA_NAME=$(echo "$POSSIBLE_SCHEMAS" | head -1)
        print_info "Found possible schema: $SCHEMA_NAME"
        SCHEMA=$(echo "$API_DOCS" | jq -r --arg entity "$SCHEMA_NAME" '.components.schemas[$entity]' 2>/dev/null)
    fi
fi

if [ "$SCHEMA" = "null" ] || [ -z "$SCHEMA" ]; then
    print_error "Cannot proceed without schema information"
    exit 1
fi

# Extract properties from schema
print_info "Schema properties found:"
echo "$SCHEMA" | jq -r '.properties | keys[]' 2>/dev/null
echo ""

# Generate test data based on schema
print_step "Generating test data based on schema"

# Build JSON dynamically based on schema properties with correct formats
TEST_DATA=$(echo "$SCHEMA" | jq -r '
    .properties | 
    with_entries(
        if .value.type == "integer" then
            if .key | test("intValue"; "") then
                .value = 100
            elif .key | test("bigintValue"; "") then
                .value = 100
            else
                .value = 1001
            end
        elif .value.type == "number" then
            if .key | test("floatValue"; "") then
                .value = 99.99
            elif .key | test("doubleValue"; "") then
                .value = 99.99
            else
                .value = 99.99
            end
        elif .value.type == "boolean" then
            .value = true
        elif .value.type == "string" then
            if .value.format == "date" then
                .value = "2025-09-13"
            elif .value.format == "date-time" or (.key | test("timestamp"; "i")) then
                .value = "2025-09-13T12:00:00Z"
            elif .value.format == "byte" or (.key | test("blob"; "i")) then
                .value = "VGVzdCBkYXRh"
            elif .key | test("email"; "i") then
                .value = "test@example.com"
            elif .key | test("phone"; "i") then
                .value = "555-0100"
            elif .key | test("status"; "i") then
                .value = "ACTIVE"
            elif .key | test("id|Id|ID"; "") then
                .value = "test-001"
            elif .key | test("textValue"; "") then
                .value = "test"
            elif .key | test("timeValue"; "") then
                .value = "12:00:00"
            else
                .value = "Test Value"
            end
        elif .value.type == "array" then
            .value = []
        elif .value.type == "object" or (.value | has("$ref")) then
            .value = null
        else
            .value = null
        end
    )' 2>/dev/null || echo '{}')

# If generation failed, try a simpler approach
if [ "$TEST_DATA" = "{}" ] || [ -z "$TEST_DATA" ]; then
    print_info "Using fallback data generation"
    
    # Extract just the property names and types
    PROPS=$(echo "$SCHEMA" | jq -r '.properties | to_entries[] | "\(.key):\(.value.type)"' 2>/dev/null)
    
    # Build JSON manually
    TEST_DATA="{"
    FIRST=true
    while IFS=: read -r key type; do
        if [ "$FIRST" = true ]; then
            FIRST=false
        else
            TEST_DATA+=","
        fi
        
        case "$type" in
            "integer")
                if [[ "$key" =~ [iI]d|pk|PK|Pk ]]; then
                    TEST_DATA+="\"$key\":1001"
                else
                    TEST_DATA+="\"$key\":100"
                fi
                ;;
            "number")
                TEST_DATA+="\"$key\":99.99"
                ;;
            "boolean")
                TEST_DATA+="\"$key\":true"
                ;;
            "string")
                if [[ "$key" =~ dateValue|date|Date ]]; then
                    TEST_DATA+="\"$key\":\"2025-09-13\""
                elif [[ "$key" =~ timeValue|time|Time ]]; then
                    TEST_DATA+="\"$key\":\"12:00:00\""
                elif [[ "$key" =~ timestamp|Timestamp ]]; then
                    TEST_DATA+="\"$key\":\"$(date -Iseconds)\""
                elif [[ "$key" =~ email|Email ]]; then
                    TEST_DATA+="\"$key\":\"test@example.com\""
                elif [[ "$key" =~ name|Name ]]; then
                    TEST_DATA+="\"$key\":\"Test Name\""
                elif [[ "$key" =~ status|Status ]]; then
                    TEST_DATA+="\"$key\":\"ACTIVE\""
                else
                    TEST_DATA+="\"$key\":\"Test Value\""
                fi
                ;;
            *)
                TEST_DATA+="\"$key\":null"
                ;;
        esac
    done <<< "$PROPS"
    TEST_DATA+="}"
fi

echo "Generated test data:"
echo "$TEST_DATA" | jq '.' 2>/dev/null || echo "$TEST_DATA"
echo ""

# Extract primary key field(s) - check endpoint patterns for composite keys
print_step "Identifying primary key fields"

# First, find the scan-by-pk endpoint to identify partition keys
SCAN_BY_PK_ENDPOINT=$(echo "$API_DOCS" | jq -r '.paths | keys[]' 2>/dev/null | grep -E "/${ENTITY_KEBAB}/scan-by-pk/\{" | head -1)

# Extract partition key fields from scan-by-pk endpoint
if [ ! -z "$SCAN_BY_PK_ENDPOINT" ]; then
    PARTITION_KEYS=$(echo "$SCAN_BY_PK_ENDPOINT" | grep -oE '\{[^}]+\}' | sed 's/[{}]//g')
    PARTITION_KEYS_ARRAY=($PARTITION_KEYS)
    print_info "Detected partition key(s): ${PARTITION_KEYS_ARRAY[*]}"
else
    PARTITION_KEYS_ARRAY=()
fi

# Check if endpoints indicate composite key pattern (2 or more path parameters)
# Look for GET endpoint with parameters for this entity
COMPOSITE_KEY_ENDPOINT=$(echo "$API_DOCS" | jq -r '.paths | keys[]' 2>/dev/null | grep -E "/${ENTITY_KEBAB}/\{[^}]+\}/\{[^}]+\}" | grep -v "scan-by-pk" | head -1)

if [ ! -z "$COMPOSITE_KEY_ENDPOINT" ]; then
    # Extract all key components from endpoint path (PK + CK)
    ALL_KEYS=$(echo "$COMPOSITE_KEY_ENDPOINT" | grep -oE '\{[^}]+\}' | sed 's/[{}]//g')
    ALL_KEYS_ARRAY=($ALL_KEYS)
    NUM_KEYS=${#ALL_KEYS_ARRAY[@]}

    print_info "Detected composite key with $NUM_KEYS components: ${ALL_KEYS_ARRAY[*]}"

    # Build full PK_PATH (for GET/DELETE - needs PK + CK)
    PK_PATH=""
    for key in "${ALL_KEYS_ARRAY[@]}"; do
        # Convert key name from snake_case to camelCase for JSON lookup
        CAMEL_KEY=$(echo "$key" | awk -F_ '{for(i=1;i<=NF;i++){if(i==1){printf "%s",$i}else{printf "%s",toupper(substr($i,1,1)) substr($i,2)}}}')

        # Try to get value from test data
        KEY_VALUE=$(echo "$TEST_DATA" | jq -r --arg k "$CAMEL_KEY" '.[$k] // empty' 2>/dev/null)

        if [ -z "$KEY_VALUE" ]; then
            # Fallback: try the original key name
            KEY_VALUE=$(echo "$TEST_DATA" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null)
        fi

        if [ ! -z "$KEY_VALUE" ]; then
            PK_PATH="${PK_PATH}/${KEY_VALUE}"
        else
            print_error "Could not find value for key: $key (or $CAMEL_KEY) in test data"
        fi
    done

    print_info "Full key path (for GET/DELETE): $PK_PATH"

    # Build SCAN_PK_PATH (for scan-by-pk - needs only PK)
    SCAN_PK_PATH="/scan-by-pk"
    for key in "${PARTITION_KEYS_ARRAY[@]}"; do
        # Convert key name from snake_case to camelCase for JSON lookup
        CAMEL_KEY=$(echo "$key" | awk -F_ '{for(i=1;i<=NF;i++){if(i==1){printf "%s",$i}else{printf "%s",toupper(substr($i,1,1)) substr($i,2)}}}')

        # Try to get value from test data
        KEY_VALUE=$(echo "$TEST_DATA" | jq -r --arg k "$CAMEL_KEY" '.[$k] // empty' 2>/dev/null)

        if [ -z "$KEY_VALUE" ]; then
            # Fallback: try the original key name
            KEY_VALUE=$(echo "$TEST_DATA" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null)
        fi

        if [ ! -z "$KEY_VALUE" ]; then
            SCAN_PK_PATH="${SCAN_PK_PATH}/${KEY_VALUE}"
        fi
    done

    print_info "Partition key path (for scan-by-pk): $SCAN_PK_PATH"
else
    # Single key table
    # Try to find single ID field from the schema
    PK_FIELDS=$(echo "$SCHEMA" | jq -r '.properties | keys[]' 2>/dev/null | grep -E "^(id|.*Id|.*_id|pk|.*Pk.*|.*_pk)$" | head -1)

    if [ -z "$PK_FIELDS" ]; then
        # Fallback: use first property
        PK_FIELDS=$(echo "$SCHEMA" | jq -r '.properties | keys[0]' 2>/dev/null)
    fi

    print_info "Using single primary key field: $PK_FIELDS"
    PK_VALUE=$(echo "$TEST_DATA" | jq -r --arg pk "$PK_FIELDS" '.[$pk]' 2>/dev/null)
    PK_PATH="/${PK_VALUE}"
    SCAN_PK_PATH="/scan-by-pk${PK_PATH}"
    print_info "Primary key path: $PK_PATH"
fi
echo ""

# Step 3: Discover all endpoint variants
print_step "Discovering all endpoint variants"

# First, get all base endpoints that match the entity (filter out sub-paths)
ALL_MATCHING=$(echo "$API_DOCS" | jq -r '
    .paths |
    keys[] |
    select(test("/'${ENTITY_KEBAB}'"; ""))
' 2>/dev/null | grep -E "^/${ENTITY_KEBAB}[^/]*$" | sort -u)

if [ ! -z "$ALL_MATCHING" ]; then
    print_info "All matching endpoints found in API docs:"
    echo "$ALL_MATCHING" | while read endpoint; do
        echo "    $endpoint"
    done
    echo ""
fi

# Extract base paths using array for proper iteration
# Get unique base paths from all matching endpoints
declare -a ENDPOINT_VARIANTS_ARRAY
declare -A SEEN_PATHS

while IFS= read -r path; do
    # Skip empty lines
    [ -z "$path" ] && continue

    # Extract base path (first segment after /)
    base_path=$(echo "$path" | cut -d'/' -f2)

    # Skip if already processed
    [ "${SEEN_PATHS[$base_path]}" = "1" ] && continue
    SEEN_PATHS[$base_path]=1

    # Filter logic:
    # Include if: standard, -oo, or -bff variants
    # Exclude if: -one-pc, -two-pc, -one-pc-sql, -two-pc-sql (without -bff)
    # These participant endpoints are called from BFF, not directly
    if [[ "$base_path" =~ -one-pc$ ]] || [[ "$base_path" =~ -two-pc$ ]] || \
       [[ "$base_path" =~ -one-pc-sql$ ]] || [[ "$base_path" =~ -two-pc-sql$ ]]; then
        # This is a participant endpoint - skip it (called from BFF)
        print_info "Skipping participant endpoint: /${base_path} (requires Transaction ID from BFF)"
        continue
    else
        # Include this endpoint
        ENDPOINT_VARIANTS_ARRAY+=("/${base_path}")
    fi
done <<< "$ALL_MATCHING"

if [ ${#ENDPOINT_VARIANTS_ARRAY[@]} -eq 0 ]; then
    print_info "No endpoint variants found, using standard path: ${API_PATH}"
    ENDPOINT_VARIANTS_ARRAY=("${API_PATH}")
else
    print_info "Testing the following endpoint variants (${#ENDPOINT_VARIANTS_ARRAY[@]} total):"
    for variant in "${ENDPOINT_VARIANTS_ARRAY[@]}"; do
        if [[ "$variant" =~ -oo$ ]]; then
            echo "  • ${BASE_URL}${variant} (One-Operation)"
        elif [[ "$variant" =~ -one-pc-bff$ ]]; then
            echo "  • ${BASE_URL}${variant} (OnePC BFF)"
        elif [[ "$variant" =~ -two-pc-bff$ ]]; then
            echo "  • ${BASE_URL}${variant} (TwoPC BFF)"
        else
            echo "  • ${BASE_URL}${variant} (Standard)"
        fi
    done
    echo ""
fi
echo ""

# Convert to full URLs and test each variant
for VARIANT_PATH in "${ENDPOINT_VARIANTS_ARRAY[@]}"; do
    VARIANT_URL="${BASE_URL}${VARIANT_PATH}"
    VARIANT_NAME=$(echo "$VARIANT_PATH" | sed 's|^/||' | sed 's|-| |g' | sed 's/\b\w/\U&/g')

    echo "=========================================="
    echo "Testing ${VARIANT_NAME} API Variant"
    echo "API Path: ${VARIANT_URL}"
    echo "=========================================="
    echo ""

# Test 0: Cleanup - Delete test record if it exists (to avoid "already exists" errors)
print_step "0. Cleanup - Removing existing test records if any"

CLEANUP_RESPONSE=$(curl -s -X DELETE "${VARIANT_URL}${PK_PATH}" 2>/dev/null)

if echo "$CLEANUP_RESPONSE" | jq -e '.success == true' >/dev/null 2>&1; then
    print_info "Cleaned up existing test record"
elif echo "$CLEANUP_RESPONSE" | grep -q "404"; then
    print_info "No existing test record found (404 - this is fine)"
elif echo "$CLEANUP_RESPONSE" | jq -e '.message' 2>/dev/null | grep -q "DB-CORE-20018\|does not exist\|DeleteIfExists"; then
    print_info "No existing test record found (DB-CORE-20018 - this is fine)"
else
    CLEANUP_MSG=$(echo "$CLEANUP_RESPONSE" | jq -r '.message // "Unknown"' 2>/dev/null || echo "N/A")
    print_info "Cleanup result: $CLEANUP_MSG"
fi
echo ""

# Test 1: POST - Create new record
print_step "1. POST - Creating new ${ENTITY_NAME} record"

echo "Request body:"
echo "$TEST_DATA" | jq '.'

RESPONSE=$(curl -s -X POST \
  "${VARIANT_URL}" \
  -H "${CONTENT_TYPE}" \
  -d "${TEST_DATA}")

print_response "$RESPONSE"

# Check if response indicates success
if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "POST failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "POST" "NG"
else
    print_success "POST completed"
    record_result "$VARIANT_PATH" "POST" "OK"
fi
sleep 1
echo ""

# Test 2: GET - Retrieve the posted data
print_step "2. GET - Retrieving ${ENTITY_NAME} by ID"

RESPONSE=$(curl -s -X GET "${VARIANT_URL}${PK_PATH}")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "GET failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "GET" "NG"
else
    print_success "GET by ID completed"
    record_result "$VARIANT_PATH" "GET" "OK"
fi
sleep 1
echo ""

# Test 3: PUT - Update the record
print_step "3. PUT - Updating ${ENTITY_NAME} record"

# Modify test data for update
UPDATE_DATA=$(echo "$TEST_DATA" | jq '
    with_entries(
        if .key | test("name|Name"; "") then
            .value = "Updated " + .value
        elif .key | test("status|Status"; "") then
            .value = "INACTIVE"
        elif .key | test("description|Description"; "") then
            .value = "Updated via API test"
        else
            .
        end
    )' 2>/dev/null || echo "$TEST_DATA")

echo "Update body:"
echo "$UPDATE_DATA" | jq '.'

RESPONSE=$(curl -s -X PUT \
  "${VARIANT_URL}" \
  -H "${CONTENT_TYPE}" \
  -d "${UPDATE_DATA}")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "PUT failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "PUT" "NG"
else
    print_success "PUT completed"
    record_result "$VARIANT_PATH" "PUT" "OK"
fi
sleep 1
echo ""

# Test 4: GET scan-by-pk - Get records by partition key
print_step "4. GET - Scanning ${ENTITY_NAME} by partition key"

# SCAN_PK_PATH was already built in the key identification section
# It contains only partition key(s), not clustering key(s)
RESPONSE=$(curl -s -X GET "${VARIANT_URL}${SCAN_PK_PATH}")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "GET scan-by-pk failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "SCAN-BY-PK" "NG"
else
    print_success "GET scan-by-pk completed"
    record_result "$VARIANT_PATH" "SCAN-BY-PK" "OK"
fi
sleep 1
echo ""

# Test 5: POST upsert - Upsert record
print_step "5. POST - Upserting ${ENTITY_NAME} record"

# Create new data for upsert with different values
# Only modify fields that are NOT primary keys
UPSERT_DATA=$(echo "$TEST_DATA" | jq '
    if has("textValue") then .textValue = "upserted record" else . end |
    if has("simpleText") then .simpleText = "upserted text" else . end
' 2>/dev/null || echo "$TEST_DATA")

echo "Upsert body:"
echo "$UPSERT_DATA" | jq '.'

RESPONSE=$(curl -s -X POST \
  "${VARIANT_URL}/upsert" \
  -H "${CONTENT_TYPE}" \
  -d "${UPSERT_DATA}")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "POST upsert failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "UPSERT" "NG"
else
    print_success "POST upsert completed"
    record_result "$VARIANT_PATH" "UPSERT" "OK"
    # Upsert uses same keys as the original record, so cleanup path is the same as PK_PATH
    UPSERT_PK_PATH="$PK_PATH"
fi
sleep 1
echo ""

# Test 6: GET scan-all - Get all records
print_step "6. GET - Scanning all ${ENTITY_NAME} records"

RESPONSE=$(curl -s -X GET "${VARIANT_URL}/scan-all")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "GET scan-all failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "SCAN-ALL" "NG"
else
    print_success "GET scan-all completed"
    record_result "$VARIANT_PATH" "SCAN-ALL" "OK"
fi
sleep 1
echo ""

# Test 7: DELETE - Delete the record
print_step "7. DELETE - Deleting ${ENTITY_NAME} record"

RESPONSE=$(curl -s -X DELETE "${VARIANT_URL}${PK_PATH}")

print_response "$RESPONSE"

if echo "$RESPONSE" | jq -e '.success == false' >/dev/null 2>&1 || echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
    print_error "DELETE failed: $ERROR_MSG"
    record_result "$VARIANT_PATH" "DELETE" "NG"
else
    print_success "DELETE completed"
    record_result "$VARIANT_PATH" "DELETE" "OK"
fi
echo ""

# Cleanup: Delete all remaining records
print_step "Cleanup - Removing all remaining test records"

# Step 1: Get all records using scan-all
SCAN_RESPONSE=$(curl -s -X GET "${VARIANT_URL}/scan-all")

# Step 2: Extract and delete each record
if echo "$SCAN_RESPONSE" | jq -e '.data' >/dev/null 2>&1; then
    # Extract data array
    RECORDS=$(echo "$SCAN_RESPONSE" | jq -c '.data[]' 2>/dev/null)

    if [ ! -z "$RECORDS" ]; then
        RECORD_COUNT=$(echo "$RECORDS" | wc -l)
        print_info "Found $RECORD_COUNT record(s) to clean up"

        # Loop through each record and delete
        echo "$RECORDS" | while IFS= read -r record; do
            # Build delete path from record keys
            DELETE_PATH=""
            for key in "${ALL_KEYS_ARRAY[@]}"; do
                # Convert key name from snake_case to camelCase
                CAMEL_KEY=$(echo "$key" | awk -F_ '{for(i=1;i<=NF;i++){if(i==1){printf "%s",$i}else{printf "%s",toupper(substr($i,1,1)) substr($i,2)}}}')

                # Get value from record
                KEY_VALUE=$(echo "$record" | jq -r --arg k "$CAMEL_KEY" '.[$k] // empty' 2>/dev/null)

                if [ -z "$KEY_VALUE" ]; then
                    # Fallback: try the original key name
                    KEY_VALUE=$(echo "$record" | jq -r --arg k "$key" '.[$k] // empty' 2>/dev/null)
                fi

                if [ ! -z "$KEY_VALUE" ]; then
                    DELETE_PATH="${DELETE_PATH}/${KEY_VALUE}"
                fi
            done

            # Delete the record
            if [ ! -z "$DELETE_PATH" ]; then
                print_info "Deleting record at: ${DELETE_PATH}"
                DELETE_RESPONSE=$(curl -s -X DELETE "${VARIANT_URL}${DELETE_PATH}")

                if echo "$DELETE_RESPONSE" | jq -e '.success == true' >/dev/null 2>&1; then
                    print_info "✓ Deleted successfully"
                else
                    ERROR_MSG=$(echo "$DELETE_RESPONSE" | jq -r '.message // "Unknown error"' 2>/dev/null)
                    print_info "✗ Delete failed: $ERROR_MSG"
                fi
            fi
        done

        print_success "Cleanup completed"
    else
        print_info "No records found to clean up"
    fi
else
    print_info "Could not retrieve records for cleanup (this is acceptable)"
fi
echo ""

done  # End of endpoint variant loop

echo "=========================================="
echo -e "${GREEN}Smart API Testing completed!${NC}"
echo "=========================================="
echo ""

# If there were any failures, run diagnostic script for troubleshooting
FAILED_OPERATIONS=$(grep -c "✗" /tmp/smart_test_log 2>/dev/null || echo "0")

if [ "$FAILED_OPERATIONS" -gt 0 ]; then
    print_step "Some operations failed. Running diagnostic analysis..."
    
    if [ -f "${SCRIPT_DIR}/diagnose-api.sh" ]; then
        echo ""
        echo "--- Diagnostic Analysis ---"
        "${SCRIPT_DIR}/diagnose-api.sh" "${ENTITY_NAME}" "${BASE_URL}"
        echo "--- End Diagnostic Analysis ---"
    else
        print_info "diagnose-api.sh not found for troubleshooting"
    fi
else
    print_success "All operations completed successfully!"
    
    # Optionally run diagnostic for verification
    if [ "${VERBOSE:-}" = "true" ] && [ -f "${SCRIPT_DIR}/diagnose-api.sh" ]; then
        print_step "Running diagnostic verification (verbose mode)"
        echo ""
        echo "--- Diagnostic Verification ---"
        "${SCRIPT_DIR}/diagnose-api.sh" "${ENTITY_NAME}" "${BASE_URL}" | grep -E "(✓|✗|Success|Failed)" | head -10
        echo "--- End Diagnostic Verification ---"
    fi
fi

echo ""
echo "=========================================="
echo "Complete API Analysis Summary"
echo "=========================================="
echo "• Schema Discovery: Completed"
echo "• Data Format Validation: Completed"
echo "• CRUD Operations Testing: Completed"
if [ "$FAILED_OPERATIONS" -gt 0 ]; then
    echo "• Troubleshooting Analysis: Completed"
fi
echo ""

# Display test results summary table
echo "=========================================="
echo "Test Results Summary"
echo "=========================================="
echo ""

# Get unique endpoints
ENDPOINTS=$(cut -d'|' -f1 "$RESULTS_FILE" | grep -v '^$' | sort -u)
OPERATIONS="POST GET PUT SCAN-BY-PK UPSERT SCAN-ALL DELETE"

# Print header
printf "%-35s" "Endpoint"
for op in $OPERATIONS; do
    printf "%-12s" "$op"
done
echo ""
printf "%s\n" "$(printf '=%.0s' {1..120})"

# Print results for each endpoint
for endpoint in $ENDPOINTS; do
    printf "%-35s" "$endpoint"
    for op in $OPERATIONS; do
        result=$(grep "^${endpoint}|${op}|" "$RESULTS_FILE" | cut -d'|' -f3 | tail -1)
        if [ "$result" = "OK" ]; then
            printf "${GREEN}%-12s${NC}" "OK"
        elif [ "$result" = "NG" ]; then
            printf "${RED}%-12s${NC}" "NG"
        else
            printf "%-12s" "-"
        fi
    done
    echo ""
done

echo ""

# Count totals
TOTAL_OK=$(grep -c "|OK$" "$RESULTS_FILE" 2>/dev/null) || TOTAL_OK=0
TOTAL_NG=$(grep -c "|NG$" "$RESULTS_FILE" 2>/dev/null) || TOTAL_NG=0
TOTAL_TESTS=$((TOTAL_OK + TOTAL_NG))

echo "----------------------------------------"
printf "Total: ${GREEN}%d OK${NC} / ${RED}%d NG${NC} (out of %d tests)\n" "$TOTAL_OK" "$TOTAL_NG" "$TOTAL_TESTS"
echo "----------------------------------------"
echo ""

echo "For detailed analysis, run individually:"
echo "  ./get-schema.sh ${ENTITY_NAME} ${BASE_URL}"
echo "  ./diagnose-api.sh ${ENTITY_NAME} ${BASE_URL}"
echo "=========================================="

# Cleanup
rm -f /tmp/smart_test_log 2>/dev/null || true
rm -f "$RESULTS_FILE" 2>/dev/null || true