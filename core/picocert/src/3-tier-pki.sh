#!/usr/bin/env bash
set -euo pipefail

# Issue a 3-tier certificate chain.

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <ca-tool> <purpose>"
    exit 1
fi

CA_TOOL=$1
PURPOSE=$2

read -p "Before you use this in production, think hard about the validity periods! Type 'yes' to continue: " confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Fission mailed."
    exit 1
fi

# Generate a root CA key and certificate, 10 year validity.
$CA_TOOL issue --subject $PURPOSE-root --validity-in-days 3650 --self-signed

# Generate an intermediate CA key and certificate, 6 year validity.
$CA_TOOL issue --subject $PURPOSE-intermediate --validity-in-days 2190 --issuer $PURPOSE-root.pcrt --issuer-key $PURPOSE-root.priv.der

# And the leaf, 4 year validity.
$CA_TOOL issue --subject $PURPOSE-leaf --validity-in-days 1460 --issuer $PURPOSE-intermediate.pcrt --issuer-key $PURPOSE-intermediate.priv.der

# Validate the chain.
$CA_TOOL validate-chain --cert-chain $PURPOSE-leaf.pcrt $PURPOSE-intermediate.pcrt $PURPOSE-root.pcrt

echo "Done. Chain issued and validated."
