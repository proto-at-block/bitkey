#include "ew.h"

#include <secp256k1.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wally_core.h>

// Forward declaration for getentropy (macOS)
extern int getentropy(void* buffer, size_t length);

static void print_hex(const char* label, const uint8_t* data, size_t len) {
  printf("%s: ", label);
  for (size_t i = 0; i < len; i++) {
    printf("%02x", data[i]);
  }
  printf("\n");
}

// Platform API callbacks
static bool platform_crypto_random(uint8_t* out, size_t len) {
  // Note: ew.c incorrectly treats this as returning ew_error_t instead of bool
  // Return 0 for success (EW_OK), non-zero for failure
  return getentropy(out, len) == 0 ? 0 : 2;
}

static void platform_secure_memzero(void* p, size_t n) {
  volatile uint8_t* vp = (volatile uint8_t*)p;
  while (n--) {
    *vp++ = 0;
  }
}

static void* platform_malloc(size_t n) {
  return malloc(n);
}

static void platform_free(void* p) {
  free(p);
}

// Custom ECDSA signing/verification callbacks for testing.
// NOTE: These implementations are pointless - they just call libsecp256k1 directly,
// which is exactly what libwally does internally. They exist solely to exercise the
// custom callback code path and verify that our callback integration works correctly.

// Custom ECDSA signing using libsecp256k1 directly
static int platform_ecdsa_sign(const uint8_t* priv_key, size_t priv_key_len, const uint8_t* bytes,
                               size_t bytes_len, const uint8_t* aux_rand, size_t aux_rand_len,
                               uint32_t flags, uint8_t* bytes_out, size_t len) {
  (void)aux_rand;      // Unused in this simple implementation
  (void)aux_rand_len;  // Unused in this simple implementation
  (void)flags;         // Unused in this simple implementation

  // Create secp256k1 context
  secp256k1_context* ctx = secp256k1_context_create(SECP256K1_CONTEXT_SIGN);
  if (!ctx) {
    return WALLY_ERROR;
  }

  // Validate inputs
  if (!priv_key || priv_key_len != 32 || !bytes || bytes_len != 32 || !bytes_out) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  secp256k1_ecdsa_signature sig;

  // Sign the message hash
  if (!secp256k1_ecdsa_sign(ctx, &sig, bytes, priv_key, NULL, NULL)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  // Normalize signature to low-S form (required for Bitcoin)
  secp256k1_ecdsa_signature_normalize(ctx, &sig, &sig);

  // Check if we need compact (64 bytes) or DER format based on output buffer size
  if (len == 64) {
    // Compact format: serialize as 64 bytes (r || s)
    secp256k1_ecdsa_signature_serialize_compact(ctx, bytes_out, &sig);
  } else {
    // DER format: serialize as DER-encoded signature
    size_t sig_len = len;
    if (!secp256k1_ecdsa_signature_serialize_der(ctx, bytes_out, &sig_len, &sig)) {
      secp256k1_context_destroy(ctx);
      return WALLY_ERROR;
    }
  }
  secp256k1_context_destroy(ctx);
  return WALLY_OK;
}

// Custom ECDSA verification using libsecp256k1 directly
static int platform_ecdsa_verify(const uint8_t* pub_key, size_t pub_key_len, const uint8_t* bytes,
                                 size_t bytes_len, uint32_t flags, const uint8_t* sig,
                                 size_t sig_len) {
  (void)flags;  // Unused in this simple implementation

  // Create secp256k1 context
  secp256k1_context* ctx = secp256k1_context_create(SECP256K1_CONTEXT_VERIFY);
  if (!ctx) {
    return WALLY_ERROR;
  }

  // Validate inputs
  if (!pub_key || !bytes || bytes_len != 32 || !sig) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  // Parse public key
  secp256k1_pubkey pubkey;
  if (!secp256k1_ec_pubkey_parse(ctx, &pubkey, pub_key, pub_key_len)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  // Parse DER signature
  secp256k1_ecdsa_signature signature;
  if (!secp256k1_ecdsa_signature_parse_der(ctx, &signature, sig, sig_len)) {
    secp256k1_context_destroy(ctx);
    return WALLY_ERROR;
  }

  // Verify the signature
  int result = secp256k1_ecdsa_verify(ctx, &signature, bytes, &pubkey);
  secp256k1_context_destroy(ctx);

  return result ? WALLY_OK : WALLY_ERROR;
}

static ew_api_t platform_api = {
  .crypto_random = platform_crypto_random,
  .secure_memzero = platform_secure_memzero,
  .malloc = platform_malloc,
  .free = platform_free,
  .ecdsa_sign = platform_ecdsa_sign,
  .ecdsa_verify = platform_ecdsa_verify,
};

static void print_usage(const char* prog) {
  printf("Usage:\n");
  printf("  %s generate                    - Generate a new seed\n", prog);
  printf("  %s sign <psbt_file> [seed]     - Sign a PSBT from file (seed as hex or generate new)\n",
         prog);
  printf("\nOptions:\n");
  printf("  --testnet                       - Use testnet (default: mainnet)\n");
}

static int hex_to_bytes(const char* hex, uint8_t* out, size_t out_len) {
  size_t hex_len = strlen(hex);
  if (hex_len != out_len * 2) {
    return -1;
  }

  for (size_t i = 0; i < out_len; i++) {
    char byte_str[3] = {hex[i * 2], hex[i * 2 + 1], '\0'};
    char* endptr;
    long val = strtol(byte_str, &endptr, 16);
    if (*endptr != '\0' || val < 0 || val > 255) {
      return -1;
    }
    out[i] = (uint8_t)val;
  }
  return 0;
}

int main(int argc, char* argv[]) {
  ew_error_t err;
  bool testnet = false;

  // Check for testnet flag
  for (int i = 1; i < argc; i++) {
    if (strcmp(argv[i], "--testnet") == 0) {
      testnet = true;
      // Remove the flag from argv
      for (int j = i; j < argc - 1; j++) {
        argv[j] = argv[j + 1];
      }
      argc--;
      break;
    }
  }

  if (argc < 2) {
    print_usage(argv[0]);
    return 1;
  }

  /* Initialize library */
  err = ew_init(&platform_api);
  if (err != EW_OK) {
    fprintf(stderr, "Failed to initialize libew: %d\n", err);
    return 1;
  }

  printf("=== LibEW Demo ===\n");
  printf("Network: %s\n\n", testnet ? "testnet" : "mainnet");

  if (strcmp(argv[1], "generate") == 0) {
    /* Generate and display a seed */
    uint8_t seed[EW_SEED_SIZE];
    err = ew_seed_generate(seed);
    if (err != EW_OK) {
      fprintf(stderr, "Failed to generate seed: %d\n", err);
      ew_cleanup();
      return 1;
    }

    print_hex("Generated seed", seed, EW_SEED_SIZE);
    platform_secure_memzero(seed, EW_SEED_SIZE);

  } else if (strcmp(argv[1], "sign") == 0) {
    if (argc < 3) {
      fprintf(stderr, "Error: Missing PSBT file argument\n");
      print_usage(argv[0]);
      ew_cleanup();
      return 1;
    }

    const char* psbt_file = argv[2];
    uint8_t seed[EW_SEED_SIZE];

    // Get or generate seed
    if (argc >= 4) {
      // Use provided seed (as hex)
      if (hex_to_bytes(argv[3], seed, EW_SEED_SIZE) != 0) {
        fprintf(stderr, "Error: Invalid seed hex (must be 64 hex chars)\n");
        ew_cleanup();
        return 1;
      }
      printf("Using provided seed\n");
    } else {
      // Generate new seed
      err = ew_seed_generate(seed);
      if (err != EW_OK) {
        fprintf(stderr, "Failed to generate seed: %d\n", err);
        ew_cleanup();
        return 1;
      }
      printf("Generated new seed:\n");
      print_hex("Seed", seed, EW_SEED_SIZE);
    }

    // Read PSBT from file
    FILE* f = fopen(psbt_file, "rb");
    if (!f) {
      fprintf(stderr, "Failed to open PSBT file: %s\n", psbt_file);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }

    // Get file size
    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (file_size <= 0 || file_size > 1024 * 1024) {  // Max 1MB for safety
      fprintf(stderr, "Invalid PSBT file size: %ld\n", file_size);
      fclose(f);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }

    uint8_t* psbt_bytes = malloc(file_size);
    if (!psbt_bytes) {
      fprintf(stderr, "Failed to allocate memory for PSBT\n");
      fclose(f);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }

    size_t psbt_bytes_len = fread(psbt_bytes, 1, file_size, f);
    fclose(f);

    if (psbt_bytes_len != (size_t)file_size) {
      fprintf(stderr, "Failed to read PSBT file\n");
      free(psbt_bytes);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }

    printf("\nInput PSBT: %zu bytes\n", psbt_bytes_len);

    // Calculate the maximum size needed for the signed PSBT
    size_t psbt_out_max;
    err = ew_psbt_get_max_signed_size(psbt_bytes, psbt_bytes_len, &psbt_out_max);
    if (err != EW_OK) {
      fprintf(stderr, "Failed to calculate output size: %d\n", err);
      free(psbt_bytes);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }
    printf("Maximum output size: %zu bytes\n", psbt_out_max);

    // Allocate the calculated maximum size
    uint8_t* psbt_out = malloc(psbt_out_max);
    if (!psbt_out) {
      fprintf(stderr, "Failed to allocate memory for output PSBT\n");
      free(psbt_bytes);
      platform_secure_memzero(seed, EW_SEED_SIZE);
      ew_cleanup();
      return 1;
    }

    // Sign the PSBT
    size_t psbt_out_len = 0;
    printf("Signing PSBT...\n");
    err = ew_psbt_sign(psbt_bytes, psbt_bytes_len, psbt_out, psbt_out_max, &psbt_out_len, seed,
                       !testnet);

    platform_secure_memzero(seed, EW_SEED_SIZE);
    free(psbt_bytes);

    if (err != EW_OK) {
      const char* error_msg = "Unknown error";
      switch (err) {
        case EW_ERROR_INVALID_PSBT:
          error_msg = "Invalid PSBT format";
          break;
        case EW_ERROR_MISSING_UTXO:
          error_msg = "Missing UTXO information in PSBT";
          break;
        case EW_ERROR_NO_MATCHING_INPUTS:
          error_msg = "No inputs match the provided seed";
          break;
        case EW_ERROR_KEY_MISMATCH:
          error_msg = "No inputs match our key";
          break;
        case EW_ERROR_SIGNING_FAILED:
          error_msg = "Failed to sign matching inputs";
          break;
        default:
          break;
      }
      fprintf(stderr, "Failed to sign PSBT: %s (error code: %d)\n", error_msg, err);
      free(psbt_out);
      ew_cleanup();
      return 1;
    }

    printf("Successfully signed PSBT!\n");
    printf("Output PSBT: %zu bytes\n", psbt_out_len);

    // Write signed PSBT to output file
    char output_file[256];
    snprintf(output_file, sizeof(output_file), "signed.psbt");

    FILE* out_f = fopen(output_file, "wb");
    if (!out_f) {
      fprintf(stderr, "Failed to create output file: %s\n", output_file);
      free(psbt_out);
      ew_cleanup();
      return 1;
    }

    size_t written = fwrite(psbt_out, 1, psbt_out_len, out_f);
    fclose(out_f);

    if (written != psbt_out_len) {
      fprintf(stderr, "Failed to write output PSBT\n");
      free(psbt_out);
      ew_cleanup();
      return 1;
    }

    printf("\nSigned PSBT written to: %s\n", output_file);
    free(psbt_out);

  } else {
    fprintf(stderr, "Unknown command: %s\n", argv[1]);
    print_usage(argv[0]);
    ew_cleanup();
    return 1;
  }

  ew_cleanup();
  printf("\n=== Demo complete ===\n");
  return 0;
}
