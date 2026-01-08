/**
 * @file
 *
 * @brief Firmware Update Shared State
 *
 * @{
 */

#pragma once

#include "perf.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

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
} fwup_priv_t;

/** @} */
