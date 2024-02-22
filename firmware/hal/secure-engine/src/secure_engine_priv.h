#pragma once

#include "secure_engine.h"

sl_status_t sl_se_read_otp(sl_se_command_context_t* cmd_ctx, sl_se_otp_init_t* otp_settings);
sl_status_t sl_se_get_se_version(sl_se_command_context_t* cmd_ctx, uint32_t* version);
sl_status_t sl_se_get_debug_lock_status(sl_se_command_context_t* cmd_ctx,
                                        sl_se_debug_status_t* debug_status);
sl_status_t sl_se_get_status(sl_se_command_context_t* cmd_ctx, sl_se_status_t* status);
sl_status_t sl_se_get_serialnumber(sl_se_command_context_t* cmd_ctx, void* serial);
sl_status_t sl_se_get_otp_version(sl_se_command_context_t* cmd_ctx, uint32_t* version);

sl_status_t sl_se_read_cert(sl_se_command_context_t* cmd_ctx, sl_se_cert_type_t cert_type,
                            void* cert, uint32_t num_bytes);
sl_status_t sl_se_read_pubkey(sl_se_command_context_t* cmd_ctx, sl_se_device_key_type_t key_type,
                              void* key, uint32_t num_bytes);
sl_status_t sl_se_read_cert_size(sl_se_command_context_t* cmd_ctx,
                                 sl_se_cert_size_type_t* cert_size);
