#!/bin/bash

# Schema Discovery Script - Extracts entity schema from Swagger/OpenAPI
# Usage: ./get-schema.sh <entity_name> [base_url]
# Example: ./get-schema.sh TestP1 http://localhost:8080

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
    echo -e "${BLUE}$1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ $1${NC}"
}

# Check if entity name is provided
if [ -z "$ENTITY_NAME" ]; then
    echo "Usage: $0 <entity_name> [base_url]"
    echo "Example: $0 TestP1 http://localhost:8080"
    echo ""
    echo "This script will show you:"
    echo "  - Available schemas in the API"
    echo "  - Schema properties for your entity"
    echo "  - Sample test data based on the schema"
    exit 1
fi

echo "=========================================="
echo -e "${MAGENTA}Schema Discovery for ${ENTITY_NAME}${NC}"
echo "Base URL: ${BASE_URL}"
echo "=========================================="
echo ""

# Fetch OpenAPI documentation
print_header "Fetching API documentation..."

API_DOCS=$(curl -s "${BASE_URL}/v3/api-docs" 2>/dev/null)

if [ -z "$API_DOCS" ] || [ "$API_DOCS" = "null" ]; then
    print_error "Could not fetch API documentation from ${BASE_URL}/v3/api-docs"
    echo "Make sure your Spring Boot application is running with Swagger enabled"
    exit 1
fi

print_success "API documentation retrieved"
echo ""

# List all available schemas
print_header "Available schemas in the API:"
echo "$API_DOCS" | jq -r '.components.schemas | keys[]' 2>/dev/null | sort
echo ""

# Search for entity-related schemas
print_header "Searching for ${ENTITY_NAME} related schemas:"

# Try different naming patterns
PATTERNS=("${ENTITY_NAME}Dto" "${ENTITY_NAME}" "${ENTITY_NAME}DTO" "$(echo $ENTITY_NAME | tr '[:upper:]' '[:lower:]')Dto")

FOUND_SCHEMA=""
SCHEMA_NAME=""

for PATTERN in "${PATTERNS[@]}"; do
    # Check exact match
    if echo "$API_DOCS" | jq -e --arg name "$PATTERN" '.components.schemas[$name]' > /dev/null 2>&1; then
        FOUND_SCHEMA=$(echo "$API_DOCS" | jq -r --arg name "$PATTERN" '.components.schemas[$name]')
        SCHEMA_NAME="$PATTERN"
        print_success "Found exact match: $PATTERN"
        break
    fi
    
    # Check case-insensitive match
    MATCHES=$(echo "$API_DOCS" | jq -r '.components.schemas | keys[]' | grep -i "$PATTERN" || true)
    if [ ! -z "$MATCHES" ]; then
        SCHEMA_NAME=$(echo "$MATCHES" | head -1)
        FOUND_SCHEMA=$(echo "$API_DOCS" | jq -r --arg name "$SCHEMA_NAME" '.components.schemas[$name]')
        print_success "Found match: $SCHEMA_NAME"
        break
    fi
done

if [ -z "$FOUND_SCHEMA" ] || [ "$FOUND_SCHEMA" = "null" ]; then
    print_error "No schema found for ${ENTITY_NAME}"
    echo ""
    print_info "Available schemas that might be related:"
    echo "$API_DOCS" | jq -r '.components.schemas | keys[]' | grep -i "$(echo $ENTITY_NAME | sed 's/[0-9]*$//' | tr '[:upper:]' '[:lower:]')" || echo "  None found"
    exit 1
fi

echo ""

# Display schema details
print_header "Schema: $SCHEMA_NAME"
echo ""

print_info "Full schema definition:"
echo "$FOUND_SCHEMA" | jq '.'
echo ""

# Extract and display properties
print_header "Properties:"
echo "$FOUND_SCHEMA" | jq -r '
    .properties | to_entries[] | 
    "  \(.key): \(.value.type // "object") \(if .value.format then "(\(.value.format))" else "" end)"
' 2>/dev/null
echo ""

# Extract required fields
REQUIRED_FIELDS=$(echo "$FOUND_SCHEMA" | jq -r '.required[]?' 2>/dev/null)
if [ ! -z "$REQUIRED_FIELDS" ]; then
    print_header "Required fields:"
    echo "$REQUIRED_FIELDS" | while read -r field; do
        echo "  - $field"
    done
    echo ""
fi

# Generate sample test data
print_header "Sample test data based on schema:"

SAMPLE_DATA="{"
FIRST=true

# Process each property
while IFS= read -r line; do
    if [ ! -z "$line" ]; then
        KEY=$(echo "$line" | jq -r '.key')
        TYPE=$(echo "$line" | jq -r '.type')
        FORMAT=$(echo "$line" | jq -r '.format // ""')
        REF=$(echo "$line" | jq -r '.["$ref"] // ""')
        
        if [ "$FIRST" = true ]; then
            FIRST=false
        else
            SAMPLE_DATA+=","
        fi
        SAMPLE_DATA+=$'\n  '
        
        # Generate appropriate test value based on type, format, and name
        case "$TYPE" in
            "integer")
                if [[ "$KEY" =~ intValue ]]; then
                    SAMPLE_DATA+="\"$KEY\": 100"
                elif [[ "$KEY" =~ bigintValue ]]; then
                    SAMPLE_DATA+="\"$KEY\": 100"
                elif [[ "$KEY" =~ [iI]d|pk|PK|Pk ]]; then
                    if [[ "$KEY" =~ pk1|PK1|Pk1 ]]; then
                        SAMPLE_DATA+="\"$KEY\": 1"
                    elif [[ "$KEY" =~ pk2|PK2|Pk2 ]]; then
                        SAMPLE_DATA+="\"$KEY\": 2"
                    else
                        SAMPLE_DATA+="\"$KEY\": 1001"
                    fi
                else
                    SAMPLE_DATA+="\"$KEY\": 100"
                fi
                ;;
            "number")
                if [[ "$KEY" =~ floatValue|doubleValue ]]; then
                    SAMPLE_DATA+="\"$KEY\": 99.99"
                elif [ "$FORMAT" = "double" ] || [ "$FORMAT" = "float" ]; then
                    SAMPLE_DATA+="\"$KEY\": 99.99"
                else
                    SAMPLE_DATA+="\"$KEY\": 100"
                fi
                ;;
            "boolean")
                SAMPLE_DATA+="\"$KEY\": true"
                ;;
            "string")
                if [ "$FORMAT" = "date" ]; then
                    SAMPLE_DATA+="\"$KEY\": \"$(date +%Y-%m-%d)\""
                elif [ "$FORMAT" = "date-time" ] || [[ "$KEY" =~ timestamp ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"$(date +%Y-%m-%dT%H:%M:%S)Z\""
                elif [ "$FORMAT" = "byte" ] || [[ "$KEY" =~ blob|Blob ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"VGVzdCBkYXRh\""
                elif [[ "$KEY" =~ email|Email ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"test@example.com\""
                elif [[ "$KEY" =~ phone|Phone ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"555-0100\""
                elif [[ "$KEY" =~ textValue ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"test\""
                elif [[ "$KEY" =~ name|Name ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"Test $KEY\""
                elif [[ "$KEY" =~ status|Status ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"ACTIVE\""
                elif [[ "$KEY" =~ description|Description ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"Test description for $ENTITY_NAME\""
                elif [[ "$KEY" =~ address|Address ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"123 Test Street\""
                elif [[ "$KEY" =~ city|City ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"Test City\""
                elif [[ "$KEY" =~ country|Country ]]; then
                    SAMPLE_DATA+="\"$KEY\": \"USA\""
                else
                    SAMPLE_DATA+="\"$KEY\": \"test-value\""
                fi
                ;;
            "array")
                SAMPLE_DATA+="\"$KEY\": []"
                ;;
            "object")
                if [[ "$KEY" =~ timeValue ]] && [[ "$REF" =~ LocalTime ]]; then
                    # For LocalTime objects, use null as discovered by diagnostic
                    SAMPLE_DATA+="\"$KEY\": null"
                else
                    SAMPLE_DATA+="\"$KEY\": {}"
                fi
                ;;
            *)
                SAMPLE_DATA+="\"$KEY\": null"
                ;;
        esac
    fi
done < <(echo "$FOUND_SCHEMA" | jq -c '.properties | to_entries[] | {key: .key, type: .value.type, format: .value.format, "$ref": .value["$ref"]}' 2>/dev/null)

SAMPLE_DATA+=$'\n}'

echo "$SAMPLE_DATA" | jq '.' 2>/dev/null || echo "$SAMPLE_DATA"
echo ""

# Identify primary key fields
print_header "Likely primary key fields:"

PK_FIELDS=$(echo "$FOUND_SCHEMA" | jq -r '.properties | keys[]' 2>/dev/null | grep -E "(^id$|Id$|_id$|^pk|Pk|PK|_pk)" || true)

if [ ! -z "$PK_FIELDS" ]; then
    echo "$PK_FIELDS" | while read -r field; do
        echo "  - $field"
    done
else
    print_info "No obvious primary key fields found. Using first property as fallback."
    echo "  - $(echo "$FOUND_SCHEMA" | jq -r '.properties | keys[0]' 2>/dev/null)"
fi
echo ""

# Show endpoint information
print_header "Available endpoints for ${ENTITY_NAME}:"

ENTITY_KEBAB=$(echo "$ENTITY_NAME" | sed 's/\([A-Z]\)/-\1/g' | sed 's/^-//' | tr '[:upper:]' '[:lower:]')

echo "$API_DOCS" | jq -r --arg path "/$ENTITY_KEBAB" '
    .paths | to_entries[] | 
    select(.key | startswith($path)) | 
    "\(.key): \([.value | keys[]] | join(", ") | ascii_upcase)"
' 2>/dev/null || print_info "Could not find specific endpoints. Check with discover-endpoints.sh"

echo ""
echo "=========================================="
print_success "Schema discovery completed!"
echo ""
echo "To test this entity, you can:"
echo "  1. Save the sample data to a file: ./get-schema.sh $ENTITY_NAME > ${ENTITY_NAME}_sample.json"
echo "  2. Use the smart test script: ./test-api-smart.sh $ENTITY_NAME"
echo "  3. Create a custom test script using the schema information above"
echo "=========================================="