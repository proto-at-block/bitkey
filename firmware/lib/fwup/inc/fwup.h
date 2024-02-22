#pragma once

#include "attributes.h"
#include "wallet.pb.h"

#include <stdbool.h>

// NOTE: must be called once before any other functions are called.
void fwup_init(void* _target_slot_addr, void* _current_slot_addr, void* _target_slot_signature,
               uint32_t target_app_slot_size, bool support_bl_upgrade);

bool fwup_start(fwpb_fwup_start_cmd* cmd, fwpb_fwup_start_rsp* rsp_out);
bool fwup_transfer(fwpb_fwup_transfer_cmd* cmd, fwpb_fwup_transfer_rsp* rsp_out);
bool fwup_finish(fwpb_fwup_finish_cmd* cmd, fwpb_fwup_finish_rsp* rsp_out);

// Located in fwup_addr.c
NO_OPTIMIZE void* fwup_target_slot_address(void);
NO_OPTIMIZE void* fwup_target_slot_signature_address(void);
NO_OPTIMIZE void* fwup_current_slot_address(void);
NO_OPTIMIZE uint32_t fwup_slot_size(void);
NO_OPTIMIZE void* fwup_bl_address(void);
NO_OPTIMIZE uint32_t fwup_bl_size(void);
