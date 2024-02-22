#pragma once

#include "attributes.h"
#include "perf.h"
#include "wallet.pb.h"

#include <stdbool.h>
#include <stdint.h>

// Erase the target app slot.
bool fwup_flash_erase_app(perf_counter_t* perf, void* slot_addr);

// Erase the target app slot, but not the flash page containing the signature.
// Used for delta FWUP.
bool fwup_flash_erase_app_excluding_signature(perf_counter_t* perf, void* slot_addr);

// Erase the target app slot's page containing the signature.
bool fwup_flash_erase_app_signature(perf_counter_t* perf, void* slot_addr);

// Erase the bootloader.
bool fwup_flash_erase_bl(perf_counter_t* perf, void* slot_addr, uint32_t bl_size);

// Write to flash.
bool fwup_flash_write(perf_counter_t* perf, void* address, void const* data, uint32_t len);

typedef struct {
  fwpb_fwup_mode mode;
  size_t patch_size;
  uint32_t active_slot_base_addr;
  uint32_t target_slot_base_addr;
} fwup_delta_cfg_t;

bool fwup_delta_init(fwup_delta_cfg_t cfg, perf_counter_t* perf_flash_write,
                     perf_counter_t* perf_erase);
bool fwup_delta_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out);
bool fwup_delta_finish(fwpb_fwup_finish_cmd* cmd);
