#!/bin/bash
#
# Run RS-485 hardware tests
#
# Prerequisites:
#   1. Copy the example config and edit with your settings:
#      cp hardware-test/src/test/resources/rs485-test-config.example.yaml \
#         hardware-test/src/test/resources/rs485-test-config.yaml
#   2. Edit rs485-test-config.yaml with SSH credentials and serial port details
#
# Usage:
#   ./run-hardware-test.sh
#

set -e

CONFIG_FILE="hardware-test/src/test/resources/rs485-test-config.yaml"
EXAMPLE_FILE="hardware-test/src/test/resources/rs485-test-config.example.yaml"

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Configuration file not found: $CONFIG_FILE"
    echo ""
    echo "Please create the configuration file first:"
    echo "  cp $EXAMPLE_FILE \\"
    echo "     $CONFIG_FILE"
    echo ""
    echo "Then edit $CONFIG_FILE with your SSH credentials and serial port details."
    exit 1
fi

echo "=== Building test application JAR ==="
./gradlew :hardware-test:testAppJar

echo ""
echo "=== Running RS-485 hardware tests ==="
./gradlew :hardware-test:test -DhardwareTest=true "$@"
