#pragma once

#include <stddef.h>
#include <stdint.h>

/**
 * @file sim_dev_certs.h
 * @brief Embedded dev PKI certificates for core-sim attestation testing
 *
 * These certificates are generated from firmware/config/keys/dev-certs/
 * using the generate-dev-pki.sh script.
 *
 * Certificate chain:
 *   Dev Root CA -> Dev Factory -> Dev Batch -> Device (runtime generated)
 */

// Dev Root CA certificate (DER format)
extern const uint8_t dev_root_cert_der[];
extern const size_t dev_root_cert_der_len;

// Dev Factory Intermediate certificate (DER format)
extern const uint8_t dev_factory_cert_der[];
extern const size_t dev_factory_cert_der_len;

// Dev Batch certificate (DER format)
extern const uint8_t dev_batch_cert_der[];
extern const size_t dev_batch_cert_der_len;

// Batch private key in PEM format (needed for runtime signing of device certs)
// This is a DEV-ONLY key, safe to embed since it's only used for simulator testing
extern const char dev_batch_key_pem[];
extern const size_t dev_batch_key_pem_len;
