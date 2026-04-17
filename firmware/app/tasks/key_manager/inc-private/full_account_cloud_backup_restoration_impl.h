#pragma once

#include "ipc.h"

#include <stdbool.h>

void full_account_cloud_backup_restoration_handle_init(ipc_ref_t* message);
void full_account_cloud_backup_restoration_handle_continue(ipc_ref_t* message);
void full_account_cloud_backup_restoration_register_handlers(void);
bool full_account_cloud_backup_restoration_is_session_ready(void);
void full_account_cloud_backup_restoration_clear_session(void);
