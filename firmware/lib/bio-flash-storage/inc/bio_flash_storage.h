#pragma once

#include <stdbool.h>
#include <stdint.h>

// Raw flash storage for a single fingerprint template in the dedicated
// bio_flash partition. This bypasses the filesystem to avoid littlefs
// metadata overhead, allowing the full 32 KB region to store a ~17 KB template.
//
// Flash layout:
//   [0..3]   magic   (0x42494F54 = "BIOT")
//   [4..7]   version (header version, currently 1)
//   [8..11]  size    (uint32_t, little-endian, size of template data)
//   [12..15] crc32   (CRC-32 over template data)
//   [16..]   template data

#define BIO_FLASH_MAGIC          0x42494F54  // "BIOT"
#define BIO_FLASH_HEADER_VERSION 1

// Expected max template size from FPC algorithm. Verified at runtime against
// fpc_bep_algorithm_get_max_template_size(). Update this if the FPC library
// or algorithm configuration changes.
#define BIO_FLASH_HEADER_SIZE       16
#define BIO_FLASH_PARTITION_SIZE    (32 * 1024)
#define BIO_FLASH_MAX_TEMPLATE_SIZE (17560)

// Initialize the cached existence flag from flash. Must be called once
// on boot (from a privileged task) before other tasks query template existence.
// Sets flash base/capacity from linker-provided partition symbols.
void bio_flash_storage_init(void);

// Override the flash base address and capacity. Used by unit tests where
// linker-provided symbols are not available.
void bio_flash_storage_set_flash(void* base, uint32_t capacity);

bool bio_flash_storage_save(const uint8_t* data, uint32_t size);
bool bio_flash_storage_read(uint8_t* data, uint32_t* size_out);
bool bio_flash_storage_erase(void);
bool bio_flash_storage_exists(void);

// Returns whether a template exists, using the cached flag (no flash access).
// Safe to call from unprivileged tasks without MPU access to bio_flash.
// Some tasks don't have enough MPU slots remaining, so this saves an MPU slot.
bool bio_flash_storage_template_exists(void);

// Returns the maximum template size that can be stored (capacity minus header).
uint32_t bio_flash_storage_max_size(void);

// Returns the size of the stored template without reading the full data.
bool bio_flash_storage_get_size(uint32_t* size_out);

// Check that BIO_FLASH_MAX_TEMPLATE_SIZE fits in the flash partition and matches
// the FPC algorithm's reported max. Call once at init with the value returned by
// fpc_bep_algorithm_get_max_template_size(). Returns false on mismatch.
bool bio_flash_storage_check_capacity(uint32_t fpc_max_template_size);
