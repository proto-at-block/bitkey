#!/bin/bash
# Generate Dev PKI for core-sim attestation testing
#
# This script creates a certificate chain for simulator provisioning:
#   Dev Root CA -> Dev Factory -> Dev Batch -> (Device cert generated at runtime)
#
# Output files:
#   - dev-root-cert.der     (committed, used for app verification)
#   - dev-factory-cert.der  (committed, used for app verification)
#   - dev-batch-cert.der    (committed, embedded in core-sim)
#   - dev-batch.key         (committed, used at runtime to sign device certs)
#   - dev-root.key          (GITIGNORED, only needed to regenerate chain)
#   - dev-factory.key       (GITIGNORED, only needed to regenerate chain)

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "=== Generating Dev PKI for core-sim ==="
echo "Working directory: $DIR"
echo ""

# Root CA (self-signed)
echo "1. Generating Root CA..."
openssl ecparam -name prime256v1 -genkey -noout -out dev-root.key
openssl req -new -x509 -sha256 -key dev-root.key \
    -out dev-root-cert.pem \
    -subj "/C=US/O=Silicon Labs Inc./CN=Device Root CA" \
    -days 3650 \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:2" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
openssl x509 -in dev-root-cert.pem -outform DER -out dev-root-cert.der
echo "   Created: dev-root-cert.der ($(wc -c < dev-root-cert.der) bytes)"

# Factory Intermediate
echo "2. Generating Factory Intermediate..."
openssl ecparam -name prime256v1 -genkey -noout -out dev-factory.key
openssl req -new -sha256 -key dev-factory.key \
    -out dev-factory.csr \
    -subj "/C=US/O=Silicon Labs Inc./CN=Factory"
openssl x509 -req -sha256 -in dev-factory.csr \
    -CA dev-root-cert.pem -CAkey dev-root.key \
    -CAcreateserial \
    -out dev-factory-cert.pem \
    -days 1825 \
    -extfile <(printf "basicConstraints=critical,CA:TRUE,pathlen:1\nkeyUsage=critical,keyCertSign,cRLSign")
openssl x509 -in dev-factory-cert.pem -outform DER -out dev-factory-cert.der
echo "   Created: dev-factory-cert.der ($(wc -c < dev-factory-cert.der) bytes)"

# Batch Certificate (key is committed since it's needed at runtime)
echo "3. Generating Batch Certificate..."
openssl ecparam -name prime256v1 -genkey -noout -out dev-batch.key
openssl req -new -sha256 -key dev-batch.key \
    -out dev-batch.csr \
    -subj "/C=US/O=Silicon Labs Inc./CN=Batch X000099"
openssl x509 -req -sha256 -in dev-batch.csr \
    -CA dev-factory-cert.pem -CAkey dev-factory.key \
    -CAcreateserial \
    -out dev-batch-cert.pem \
    -days 1825 \
    -extfile <(printf "basicConstraints=critical,CA:TRUE,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign")
openssl x509 -in dev-batch-cert.pem -outform DER -out dev-batch-cert.der
echo "   Created: dev-batch-cert.der ($(wc -c < dev-batch-cert.der) bytes)"
echo "   Created: dev-batch.key (needed at runtime)"

# Cleanup temporary files
echo ""
echo "4. Cleaning up temporary files..."
rm -f *.csr *.srl dev-root-cert.pem dev-factory-cert.pem dev-batch-cert.pem

# Verify the chain
echo ""
echo "5. Verifying certificate chain..."
# Convert DER back to PEM for verification
openssl x509 -in dev-root-cert.der -inform DER -out /tmp/dev-root.pem
openssl x509 -in dev-factory-cert.der -inform DER -out /tmp/dev-factory.pem
openssl x509 -in dev-batch-cert.der -inform DER -out /tmp/dev-batch.pem

openssl verify -CAfile /tmp/dev-root.pem /tmp/dev-root.pem 2>/dev/null && echo "   Root CA: OK (self-signed)"
openssl verify -CAfile /tmp/dev-root.pem /tmp/dev-factory.pem 2>/dev/null && echo "   Factory: OK (signed by Root)"
openssl verify -CAfile /tmp/dev-root.pem -untrusted /tmp/dev-factory.pem /tmp/dev-batch.pem 2>/dev/null && echo "   Batch:   OK (signed by Factory)"

rm -f /tmp/dev-root.pem /tmp/dev-factory.pem /tmp/dev-batch.pem

echo ""
echo "=== Dev PKI generation complete ==="
echo ""
echo "Files to commit:"
echo "  - dev-root-cert.der"
echo "  - dev-factory-cert.der"
echo "  - dev-batch-cert.der"
echo "  - dev-batch.key"
echo ""
echo "Files to GITIGNORE (regenerate if needed):"
echo "  - dev-root.key"
echo "  - dev-factory.key"
