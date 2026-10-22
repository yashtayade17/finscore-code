#!/bin/sh
cd "$(dirname "$0")"
echo "Starting FinScore on http://localhost:8080 ..."
./mvnw spring-boot:run
