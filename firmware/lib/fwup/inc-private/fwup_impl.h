/**
 * @file
 *
 * @brief Firmware Update Shared State
 *
 * @{
 */

#pragma once

#include "ecc.h"
#include "perf.h"
#include "secutils.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/** @brief Size of the pending signature buffer. */
#define FWUP_SIGNATURE_SIZE ECC_SIG_SIZE

/**
 * @brief Shared state across FWUP sub-modules provided in #fwup_init().
 */
typedef struct {
  /**
   * @brief Performance counter for flash write/erase and FWUP transfer commands.
   */
  struct {
    perf_counter_t* erase;
    perf_counter_t* write;
    perf_counter_t* transfer;
    perf_counter_t* transfer_cmd;
  } perf;
  void* target_slot_addr;
  void* current_slot_addr;
  void* target_slot_signature;
  size_t app_slot_size;
  bool support_bl_upgrade;
  volatile secure_bool_t require_confirmation;
  bool has_confirmation_version;
  fwpb_semver confirmation_version;

  /** @brief Signature held in RAM until verification succeeds. */
  uint8_t pending_signature[FWUP_SIGNATURE_SIZE];
  /** @brief True once at least one byte of signature data has been received. */
  bool has_pending_signature;
} fwup_priv_t;

/** @} */
