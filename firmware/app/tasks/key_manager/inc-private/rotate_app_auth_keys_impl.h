#pragma once

#include "ipc.h"

#include <stdbool.h>

// Rotate app auth keys init handler (called from key_manager_task_handle_rotate_app_auth_keys)
void rotate_app_auth_keys_handle_init(ipc_ref_t* message);

// Registration and session management
void rotate_app_auth_keys_register_handlers(void);
void rotate_app_auth_keys_clear_session(void);
