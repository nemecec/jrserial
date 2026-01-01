#!/bin/bash
# Script to cross-compile and run integration tests in Docker

set -e

echo "=== Cross-compile and Test in Docker ==="
echo ""

# Build native library for Linux x86_64 using Gradle
echo "Building native library for Linux x86_64..."
./gradlew copyNative_x86_64_unknown_linux_gnu

# Build Java code and copy test dependencies (separate invocation to avoid task conflict)
echo ""
echo "Building Java code..."
./gradlew classes testClasses copyTestDependencies jar

# Build Docker image (explicitly for x86_64 to match the cross-compiled library)
echo ""
echo "Building Docker image..."
docker build --platform linux/amd64 -f Dockerfile.test -t jr-serial-test .

# Run tests
echo ""
echo "Running tests in Docker..."
docker run --rm --platform linux/amd64 jr-serial-test

echo ""
echo "=== Tests completed! ==="
