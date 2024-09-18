#!/bin/bash

# Exit on any error
set -e

# Create directories
mkdir -p staging-codesigning-pki/{root,intermediate,leaf}
cd staging-codesigning-pki

# Generate configuration files

TEN_YEARS_IN_HOURS=87660h
EIGHT_YEARS_IN_HOURS=70128h

cat > pki-config.json <<EOF
{
  "signing": {
    "default": {
      "expiry": "${TEN_YEARS_IN_HOURS}"
    },
    "profiles": {
      "root-ca": {
        "expiry": "${TEN_YEARS_IN_HOURS}",
        "usages": [
          "signing",
          "key encipherment",
          "cert sign",
          "crl sign"
        ],
        "ca_constraint": {
            "is_ca": true,
            "max_path_len": 1,
            "max_path_len_zero": false
        }
      },
      "intermediate-ca": {
        "expiry": "${EIGHT_YEARS_IN_HOURS}",
        "usages": [
          "signing",
          "key encipherment",
          "cert sign",
          "crl sign"
        ],
        "ca_constraint": {
            "is_ca": true,
            "max_path_len": 0,
            "max_path_len_zero": true
        }
      },
      "leaf": {
        "usages": [
          "signing",
          "digital signature",
          "key encipherment"
        ],
        "expiry": "${EIGHT_YEARS_IN_HOURS}"
      }
    }
  }
}
EOF

cat > root-ca-csr.json <<EOF
{
  "CN": "Nitro Enclave Codesigning Root CA",
  "key": {
    "algo": "ecdsa",
    "size": 256
  },
  "names": [
    {
      "C": "IN",
      "ST": "Blockchain",
      "L": "Cyberspace",
      "O": "Block",
      "OU": "Bitkey"
    }
  ],
  "ca": {
    "expiry": "${TEN_YEARS_IN_HOURS}"
  }
}
EOF

cat > intermediate-ca-csr.json <<EOF
{
  "CN": "Nitro Enclave Codesigning Intermediate CA",
  "key": {
    "algo": "ecdsa",
    "size": 256
  },
  "names": [
    {
      "C": "IN",
      "ST": "Blockchain",
      "L": "Cyberspace",
      "O": "Block",
      "OU": "Bitkey"
    }
  ],
  "ca": {
    "expiry": "${EIGHT_YEARS_IN_HOURS}"
  }
}
EOF

cat > leaf-csr.json <<EOF
{
  "CN": "Nitro Enclave Codesigning Leaf",
  "key": {
    "algo": "ecdsa",
    "size": 256
  },
  "names": [
    {
      "C": "IN",
      "ST": "Blockchain",
      "L": "Cyberspace",
      "O": "Block",
      "OU": "Bitkey"
    }
  ]
}
EOF

# Generate Root CA
cfssl gencert -initca root-ca-csr.json | cfssljson -bare root-ca

# Generate Intermediate CA CSR
cfssl gencert -initca intermediate-ca-csr.json | cfssljson -bare intermediate-ca

# Sign Intermediate CA with Root CA
cfssl sign -ca root-ca.pem -ca-key root-ca-key.pem -config pki-config.json -profile root-ca intermediate-ca.csr | cfssljson -bare intermediate-ca

# Generate Leaf Certificate
cfssl gencert -ca intermediate-ca.pem -ca-key intermediate-ca-key.pem -config pki-config.json -profile leaf leaf-csr.json | cfssljson -bare leaf

echo "Done."
