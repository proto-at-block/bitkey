#pragma once

#include "ipc.h"

void keyset_repair_unseal_symmetric_key_handle_init(ipc_ref_t* message);
void keyset_repair_rotate_hw_key_handle_init(ipc_ref_t* message);
void keyset_repair_register_handlers(void);
void keyset_repair_clear_session(void);
