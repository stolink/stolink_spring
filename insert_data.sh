#!/bin/bash

# Endpoint
URL="http://localhost:8080/api/internal/ai/analysis/callback"

# Check if dummy_data.json exists
if [ ! -f "dummy_data.json" ]; then
    echo "Error: dummy_data.json not found!"
    exit 1
fi

echo "Sending dummy data to $URL..."

# Send POST request
curl -X POST "$URL" \
     -H "Content-Type: application/json" \
     -d @dummy_data.json \
     -v

echo -e "\nRequest completed."
