#!/bin/bash

# API Endpoint Discovery Script
# Discovers available controllers and endpoints from Swagger/OpenAPI
# Usage: ./discover-endpoints.sh [base_url]

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
BASE_URL="${1:-http://localhost:8080}"

# Function to print colored output
print_header() {
    echo -e "${BLUE}$1${NC}"
}

print_endpoint() {
    METHOD=$1
    PATH=$2
    DESC=$3
    
    case $METHOD in
        GET)
            echo -e "  ${GREEN}GET${NC}    $PATH"
            ;;
        POST)
            echo -e "  ${YELLOW}POST${NC}   $PATH"
            ;;
        PUT)
            echo -e "  ${CYAN}PUT${NC}    $PATH"
            ;;
        DELETE)
            echo -e "  ${RED}DELETE${NC} $PATH"
            ;;
        *)
            echo -e "  ${MAGENTA}$METHOD${NC} $PATH"
            ;;
    esac
    
    if [ ! -z "$DESC" ]; then
        echo -e "         ${NC}$DESC${NC}"
    fi
}

echo "=========================================="
echo -e "${BLUE}API Endpoint Discovery${NC}"
echo "Base URL: ${BASE_URL}"
echo "=========================================="
echo ""

# Check if server is running
print_header "Checking server availability..."
if curl -s -f -o /dev/null "${BASE_URL}/swagger-ui/index.html"; then
    echo -e "${GREEN}✓ Swagger UI is available${NC}"
else
    echo -e "${RED}✗ Cannot reach Swagger UI at ${BASE_URL}/swagger-ui/index.html${NC}"
    echo "Make sure your Spring Boot application is running."
    exit 1
fi
echo ""

# Fetch OpenAPI documentation
print_header "Fetching OpenAPI documentation..."
API_DOCS=$(curl -s "${BASE_URL}/v3/api-docs" 2>/dev/null)

if [ -z "$API_DOCS" ] || [ "$API_DOCS" = "null" ]; then
    echo -e "${RED}✗ Could not fetch API documentation${NC}"
    exit 1
fi

echo -e "${GREEN}✓ API documentation retrieved${NC}"
echo ""

# Parse and display controllers
print_header "Available Controllers and Endpoints:"
echo ""

# Extract unique controller tags
CONTROLLERS=$(echo "$API_DOCS" | jq -r '.tags[].name' 2>/dev/null | sort -u)

if [ -z "$CONTROLLERS" ]; then
    # If no tags, try to extract from paths
    CONTROLLERS=$(echo "$API_DOCS" | jq -r '.paths | keys[]' 2>/dev/null | sed 's|/\([^/]*\).*|\1|' | sort -u)
fi

for CONTROLLER in $CONTROLLERS; do
    if [ ! -z "$CONTROLLER" ]; then
        echo -e "${CYAN}━━━ ${CONTROLLER} Controller ━━━${NC}"
        
        # Get all paths for this controller
        PATHS=$(echo "$API_DOCS" | jq -r --arg ctrl "$CONTROLLER" '
            .paths | to_entries[] | 
            select(.key | startswith("/\($ctrl)")) | 
            .key' 2>/dev/null)
        
        if [ -z "$PATHS" ]; then
            # Alternative: check by tags
            PATHS=$(echo "$API_DOCS" | jq -r --arg ctrl "$CONTROLLER" '
                .paths | to_entries[] | 
                select(.value[].tags[]? == $ctrl) | 
                .key' 2>/dev/null)
        fi
        
        for PATH in $PATHS; do
            # Get methods for this path
            METHODS=$(echo "$API_DOCS" | jq -r --arg path "$PATH" '
                .paths[$path] | keys[]' 2>/dev/null | grep -E "get|post|put|delete|patch" | tr '[:lower:]' '[:upper:]')
            
            for METHOD in $METHODS; do
                # Get operation summary if available
                SUMMARY=$(echo "$API_DOCS" | jq -r --arg path "$PATH" --arg method "${METHOD,,}" '
                    .paths[$path][$method].summary // ""' 2>/dev/null)
                
                print_endpoint "$METHOD" "$PATH" "$SUMMARY"
            done
        done
        echo ""
    fi
done

# Show test script usage
echo "=========================================="
echo -e "${BLUE}Testing Instructions${NC}"
echo "=========================================="
echo ""
echo "To test a specific controller, use one of the test scripts:"
echo ""
echo -e "${GREEN}Generic test (any entity):${NC}"
echo "  ./test-api.sh <entity-name> [base-url]"
echo "  Example: ./test-api.sh customer"
echo ""
echo -e "${GREEN}Specific entity tests:${NC}"
echo "  ./test-api-customer.sh [base-url]"
echo "  ./test-api-order.sh [base-url]"
echo "  ./test-api-product.sh [base-url]"
echo ""
echo -e "${GREEN}Run all tests:${NC}"
echo "  ./test-all-apis.sh [base-url]"
echo ""

# Generate quick test commands
echo "=========================================="
echo -e "${BLUE}Quick Test Commands${NC}"
echo "=========================================="
echo ""

# Extract base paths (controllers)
BASE_PATHS=$(echo "$API_DOCS" | jq -r '.paths | keys[]' 2>/dev/null | sed 's|/\([^/]*\).*|/\1|' | sort -u)

for BASE_PATH in $BASE_PATHS; do
    ENTITY=$(echo "$BASE_PATH" | sed 's|/||')
    if [ ! -z "$ENTITY" ] && [ "$ENTITY" != "error" ]; then
        echo -e "${CYAN}# Test ${ENTITY}:${NC}"
        echo "curl -X POST ${BASE_URL}${BASE_PATH} -H 'Content-Type: application/json' -d '{...}'"
        echo "curl -X GET ${BASE_URL}${BASE_PATH}/{id}"
        echo "curl -X GET ${BASE_URL}${BASE_PATH}/scan-all"
        echo "curl -X DELETE ${BASE_URL}${BASE_PATH}/{id}"
        echo ""
    fi
done

echo "=========================================="
echo -e "${GREEN}Discovery completed!${NC}"
echo "Visit ${BASE_URL}/swagger-ui/index.html for interactive API testing"
echo "=========================================="