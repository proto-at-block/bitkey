#pragma once

#include "ipc.h"

#include <stdbool.h>

// Upgrade rotate app auth keys init handler (W3 upgrade flow, no action proof signing)
void upgrade_rotate_app_auth_keys_handle_init(ipc_ref_t* message);

// Registration and session management
void upgrade_rotate_app_auth_keys_register_handlers(void);
void upgrade_rotate_app_auth_keys_clear_session(void);
