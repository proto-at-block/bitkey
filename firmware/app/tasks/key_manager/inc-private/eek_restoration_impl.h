#pragma once

#include "ipc.h"

void eek_restoration_unseal_symmetric_key_handle_init(ipc_ref_t* message);
void eek_restoration_register_handlers(void);
void eek_restoration_clear_session(void);
