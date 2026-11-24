#!/usr/bin/env bash
# Prints JWT_PRIVATE_KEY / JWT_PUBLIC_KEY lines for .env (RSA 2048, PEM, newlines as \n).
#   scripts/generate-jwt-keys.sh >> .env
set -euo pipefail
private_pem=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null)
public_pem=$(printf '%s\n' "$private_pem" | openssl rsa -pubout 2>/dev/null)
oneline() { printf '%s\n' "$1" | awk 'BEGIN{ORS="\\n"} {print}'; }
echo "JWT_PRIVATE_KEY=$(oneline "$private_pem")"
echo "JWT_PUBLIC_KEY=$(oneline "$public_pem")"
