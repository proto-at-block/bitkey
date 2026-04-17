#pragma once

#include "ipc.h"

// Sign challenge and seal SEKS init handler
void recovery_composites_sign_challenge_and_seal_handle_init(ipc_ref_t* message);

// Recovery authorize lost app init handler
void recovery_composites_authorize_lost_app_handle_init(ipc_ref_t* message);

// Recovery authorize lost hw init handler
void recovery_composites_authorize_lost_hw_handle_init(ipc_ref_t* message);

// Upgrade authorize W3 init handler (W3 upgrade: signs both SAP proofs + seals DDK)
void recovery_composites_upgrade_authorize_w3_handle_init(ipc_ref_t* message);

// Register confirmation result handlers for all composite commands
void recovery_composites_register_handlers(void);

// Clear all composite session state (called on NFC session init)
void recovery_composites_clear_sessions(void);
