#!/bin/bash

echo "🚀 Starting APISIX Control Plane"
echo "================================"
echo ""

# Check if MongoDB is running
echo "Checking MongoDB connection..."
if mongosh --eval "db.adminCommand('ping')" --quiet > /dev/null 2>&1; then
    echo "✅ MongoDB is running"
else
    echo "❌ MongoDB is not running!"
    echo "Please start MongoDB first:"
    echo "  docker run -d -p 27017:27017 --name mongodb mongo:latest"
    echo "  OR"
    echo "  mongod"
    exit 1
fi

echo ""
echo "Building and starting Control Plane..."
echo ""

# Build and run
mvn spring-boot:run

