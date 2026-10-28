#!/bin/bash

# API Diagnostic Script - Helps identify why API calls are failing
# Usage: ./diagnose-api.sh <entity_name> [base_url]
# Example: ./diagnose-api.sh TestAllTypeTest http://localhost:8080

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
ENTITY_NAME="${1:-}"
BASE_URL="${2:-http://localhost:8080}"

# Function to print colored output
print_header() {
    echo -e "${MAGENTA}━━━ $1 ━━━${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Check if entity name is provided
if [ -z "$ENTITY_NAME" ]; then
    echo "Usage: $0 <entity_name> [base_url]"
    echo "Example: $0 TestAllTypeTest http://localhost:8080"
    exit 1
fi

# Convert entity name to kebab case
ENTITY_KEBAB=$(echo "$ENTITY_NAME" | sed 's/\([A-Z]\)/-\1/g' | sed 's/^-//' | tr '[:upper:]' '[:lower:]')

echo "=========================================="
echo -e "${BLUE}API Diagnostics for ${ENTITY_NAME}${NC}"
echo "Base URL: ${BASE_URL}"
echo "Expected path: /${ENTITY_KEBAB}"
echo "=========================================="
echo ""

# Step 1: Check API Documentation
print_header "1. API Documentation Check"

API_DOCS=$(curl -s "${BASE_URL}/v3/api-docs" 2>/dev/null)

if [ -z "$API_DOCS" ] || [ "$API_DOCS" = "null" ]; then
    print_error "Cannot fetch API documentation"
    exit 1
fi

print_success "API documentation available"
echo ""

# Step 2: Find Entity Schema
print_header "2. Entity Schema Analysis"

# Try different schema name patterns
SCHEMA_PATTERNS=("${ENTITY_NAME}Dto" "${ENTITY_NAME}" "$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')Dto")
FOUND_SCHEMA=""
SCHEMA_NAME=""

for PATTERN in "${SCHEMA_PATTERNS[@]}"; do
    TEMP_SCHEMA=$(echo "$API_DOCS" | jq -r --arg name "$PATTERN" '.components.schemas[$name]' 2>/dev/null)
    if [ "$TEMP_SCHEMA" != "null" ] && [ ! -z "$TEMP_SCHEMA" ]; then
        FOUND_SCHEMA="$TEMP_SCHEMA"
        SCHEMA_NAME="$PATTERN"
        print_success "Found schema: $SCHEMA_NAME"
        break
    fi
done

if [ -z "$FOUND_SCHEMA" ] || [ "$FOUND_SCHEMA" = "null" ]; then
    print_error "No schema found for ${ENTITY_NAME}"
    print_info "Available schemas containing '$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')':"
    echo "$API_DOCS" | jq -r '.components.schemas | keys[]' | grep -i "$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')" || echo "  None"
else
    # Analyze schema structure
    echo ""
    print_info "Schema Properties:"
    echo "$FOUND_SCHEMA" | jq -r '.properties | keys[]' 2>/dev/null | while read -r prop; do
        TYPE=$(echo "$FOUND_SCHEMA" | jq -r --arg p "$prop" '.properties[$p].type' 2>/dev/null)
        FORMAT=$(echo "$FOUND_SCHEMA" | jq -r --arg p "$prop" '.properties[$p].format // ""' 2>/dev/null)
        if [ ! -z "$FORMAT" ]; then
            echo "  • $prop: $TYPE ($FORMAT)"
        else
            echo "  • $prop: $TYPE"
        fi
    done
    
    echo ""
    REQUIRED=$(echo "$FOUND_SCHEMA" | jq -r '.required[]?' 2>/dev/null)
    if [ ! -z "$REQUIRED" ]; then
        print_info "Required Fields:"
        echo "$REQUIRED" | while read -r field; do
            echo "  ✱ $field"
        done
    else
        print_info "No explicitly required fields defined"
    fi
    
    # Check for primary key fields
    echo ""
    PK_FIELDS=$(echo "$FOUND_SCHEMA" | jq -r '.properties | keys[]' 2>/dev/null | grep -E "^(id|.*Id|.*_id|pk[0-9]*|.*Pk.*|.*_pk)$" || true)
    if [ ! -z "$PK_FIELDS" ]; then
        print_info "Likely Primary Key Fields:"
        echo "$PK_FIELDS" | while read -r field; do
            echo "  🔑 $field"
        done
    fi
fi
echo ""

# Step 3: Check Endpoints
print_header "3. Available Endpoints"

ENDPOINTS=$(echo "$API_DOCS" | jq -r --arg path "/${ENTITY_KEBAB}" '
    .paths | to_entries[] | 
    select(.key | startswith($path)) | 
    .key' 2>/dev/null)

if [ -z "$ENDPOINTS" ]; then
    print_error "No endpoints found for /${ENTITY_KEBAB}"
    print_info "Trying alternative paths..."
    
    # Try to find any endpoint containing the entity name
    ALT_ENDPOINTS=$(echo "$API_DOCS" | jq -r '.paths | keys[]' 2>/dev/null | grep -i "$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')" || true)
    if [ ! -z "$ALT_ENDPOINTS" ]; then
        echo "Found alternative endpoints:"
        echo "$ALT_ENDPOINTS"
    fi
else
    for ENDPOINT in $ENDPOINTS; do
        METHODS=$(echo "$API_DOCS" | jq -r --arg ep "$ENDPOINT" '.paths[$ep] | keys[]' 2>/dev/null | grep -E "get|post|put|delete|patch" | tr '[:lower:]' '[:upper:]')
        echo "  $ENDPOINT: $(echo $METHODS | tr '\n' ', ')"
    done
fi
echo ""

# Step 4: Test POST endpoint with different formats
print_header "4. POST Endpoint Testing"

POST_ENDPOINT="${BASE_URL}/${ENTITY_KEBAB}"
echo "Testing: POST $POST_ENDPOINT"
echo ""

# Test 1: Empty object
print_info "Test 1: Empty object {}"
RESPONSE=$(curl -s -X POST "$POST_ENDPOINT" -H "Content-Type: application/json" -d '{}' 2>/dev/null)
if echo "$RESPONSE" | grep -q '"error"'; then
    ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // .error // "Unknown error"' 2>/dev/null)
    print_error "Failed: $ERROR_MSG"
else
    print_success "Accepted empty object"
fi
echo ""

# Test 2: With primary keys only
if [ ! -z "$PK_FIELDS" ]; then
    print_info "Test 2: Primary keys only"
    PK_DATA="{"
    FIRST=true
    for PK in $PK_FIELDS; do
        if [ "$FIRST" = true ]; then
            FIRST=false
        else
            PK_DATA+=","
        fi
        if [[ "$PK" =~ [0-9]$ ]]; then
            # If PK ends with number (pk1, pk2), use that number
            NUM="${PK: -1}"
            PK_DATA+="\"$PK\":$NUM"
        else
            PK_DATA+="\"$PK\":1"
        fi
    done
    PK_DATA+="}"
    
    echo "  Data: $PK_DATA"
    RESPONSE=$(curl -s -X POST "$POST_ENDPOINT" -H "Content-Type: application/json" -d "$PK_DATA" 2>/dev/null)
    if echo "$RESPONSE" | grep -q '"error"'; then
        ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // .error // "Unknown error"' 2>/dev/null)
        print_error "Failed: $ERROR_MSG"
    else
        print_success "Accepted primary keys only"
    fi
    echo ""
fi

# Test 3: With all fields
if [ ! -z "$FOUND_SCHEMA" ] && [ "$FOUND_SCHEMA" != "null" ]; then
    print_info "Test 3: All fields with sample data"
    
    # Generate comprehensive test data
    ALL_DATA=$(echo "$FOUND_SCHEMA" | jq -r '
        .properties | 
        with_entries(
            if .key | test("pk[0-9]") then
                .value = (.key | sub("pk"; "") | tonumber)
            elif .value.type == "integer" then
                .value = 100
            elif .value.type == "number" then
                .value = 99.99
            elif .value.type == "boolean" then
                .value = true
            elif .value.type == "string" then
                if .value.format == "date" then
                    .value = "2025-09-13"
                elif .value.format == "date-time" then
                    .value = "2025-09-13T12:00:00Z"
                elif .value.format == "time" then
                    .value = "12:00:00"
                elif .key | test("blob|Blob") then
                    .value = "VGVzdCBkYXRh"
                else
                    .value = "test"
                end
            else
                .value = null
            end
        )' 2>/dev/null || echo '{}')
    
    echo "  Data: $(echo $ALL_DATA | jq -c '.')"
    RESPONSE=$(curl -s -X POST "$POST_ENDPOINT" -H "Content-Type: application/json" -d "$ALL_DATA" 2>/dev/null)
    if echo "$RESPONSE" | grep -q '"error"'; then
        ERROR_MSG=$(echo "$RESPONSE" | jq -r '.message // .error // "Unknown error"' 2>/dev/null)
        print_error "Failed: $ERROR_MSG"
        
        # Try to parse validation errors
        VALIDATION_ERRORS=$(echo "$RESPONSE" | jq -r '.errors[]? // .validationErrors[]? // empty' 2>/dev/null)
        if [ ! -z "$VALIDATION_ERRORS" ]; then
            echo "  Validation errors:"
            echo "$VALIDATION_ERRORS" | while read -r err; do
                echo "    - $err"
            done
        fi
    else
        print_success "Success! Record created"
        echo "  Response: $(echo $RESPONSE | jq -c '.')"
    fi
fi
echo ""

# Step 5: Recommendations
print_header "5. Diagnostic Summary"

if [ ! -z "$FOUND_SCHEMA" ] && [ "$FOUND_SCHEMA" != "null" ]; then
    print_success "Schema found and analyzed"
else
    print_error "No schema found - check entity name"
fi

if [ ! -z "$ENDPOINTS" ]; then
    print_success "Endpoints are configured"
else
    print_error "No endpoints found - check API path"
fi

echo ""
print_info "Recommendations:"
echo "1. Check the Swagger UI for interactive testing:"
echo "   ${BASE_URL}/swagger-ui/index.html"
echo ""
echo "2. Look for example requests in Swagger UI"
echo ""
echo "3. Check server logs for detailed error messages"
echo ""
echo "4. Verify the database table exists with matching schema"
echo ""

# Generate working test command
if [ ! -z "$FOUND_SCHEMA" ] && [ "$FOUND_SCHEMA" != "null" ]; then
    echo "5. Try this curl command with the discovered schema:"
    echo ""
    echo "curl -X POST '${POST_ENDPOINT}' \\"
    echo "  -H 'Content-Type: application/json' \\"
    echo "  -d '$(echo $ALL_DATA | jq -c '.')'"
fi

echo ""
echo "=========================================="
echo "Diagnostics completed"
echo "=========================================="