#pragma once

#include "ipc.h"

#include <stdbool.h>

// Lost app recovery init handler (called from key_manager_task_handle_lost_app_recovery)
void lost_app_recovery_handle_init(ipc_ref_t* message);

// Lost app recovery sign challenge handler (confirmable auth challenge signing, W3 only)
void lost_app_recovery_sign_challenge_handle_init(ipc_ref_t* message);

// Registration and session management
void lost_app_recovery_register_handlers(void);
void lost_app_recovery_clear_session(void);

// Returns true if the LAR session has been confirmed and is ready for the continue command.
bool lost_app_recovery_is_session_ready(void);
