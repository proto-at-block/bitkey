/**
 * @file posix_fwup_addr.c
 * @brief POSIX implementation of fwup_addr functions
 *
 * This file provides POSIX implementations of the fwup_addr.h functions.
 * The embedded implementation (lib/fwup/src/fwup_addr.c) uses linker symbols
 * where the address of active_slot matches enum values - this doesn't work
 * on POSIX where we control memory layout.
 *
 * These functions MUST override the library versions (which we do by linking
 * this object file directly into the executable after the library).
 */

#include "fwup_addr.h"

#include "attributes.h"
#include "wallet.pb.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

// RAM-backed slot buffers for POSIX (no actual flash)
#define FWUP_SLOT_SIZE (632 * 1024)

static uint8_t posix_app_slot_a[FWUP_SLOT_SIZE] __attribute__((aligned(4096)));
static uint8_t posix_app_slot_b[FWUP_SLOT_SIZE] __attribute__((aligned(4096)));
static uint8_t posix_sig_a[256] __attribute__((aligned(256)));
static uint8_t posix_sig_b[256] __attribute__((aligned(256)));
static uint8_t posix_bl[64 * 1024] __attribute__((aligned(4096)));

// Current active slot - always SLOT_A on POSIX
static fwpb_firmware_slot posix_active_slot = fwpb_firmware_slot_SLOT_A;

NO_OPTIMIZE void* fwup_target_slot_address(void) {
  // Target is opposite of current slot
  if (posix_active_slot == fwpb_firmware_slot_SLOT_A) {
    return (void*)posix_app_slot_b;
  } else {
    return (void*)posix_app_slot_a;
  }
}

NO_OPTIMIZE void* fwup_target_slot_signature_address(void) {
  // Signature is opposite of current slot
  if (posix_active_slot == fwpb_firmware_slot_SLOT_A) {
    return (void*)posix_sig_b;
  } else {
    return (void*)posix_sig_a;
  }
}

NO_OPTIMIZE void* fwup_current_slot_address(void) {
  if (posix_active_slot == fwpb_firmware_slot_SLOT_A) {
    return (void*)posix_app_slot_a;
  } else {
    return (void*)posix_app_slot_b;
  }
}

NO_OPTIMIZE fwpb_firmware_slot fwup_target_slot(void) {
  // Target is opposite of current slot
  if (posix_active_slot == fwpb_firmware_slot_SLOT_A) {
    return fwpb_firmware_slot_SLOT_B;
  }
  return fwpb_firmware_slot_SLOT_A;
}

NO_OPTIMIZE void* fwup_slot_signature_address(fwpb_firmware_slot slot) {
  if (slot == fwpb_firmware_slot_SLOT_A) {
    return (void*)posix_sig_a;
  } else if (slot == fwpb_firmware_slot_SLOT_B) {
    return (void*)posix_sig_b;
  }
  return NULL;
}

NO_OPTIMIZE size_t fwup_slot_size(void) {
  return FWUP_SLOT_SIZE;
}

NO_OPTIMIZE void* fwup_bl_address(void) {
  return (void*)posix_bl;
}

NO_OPTIMIZE size_t fwup_bl_size(void) {
  return sizeof(posix_bl);
}
