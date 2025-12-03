#pragma once

#include "grant_protocol.h"

#include <stdbool.h>
#include <stdint.h>

#define GRANT_REQUEST_PATH   ("grant-request.bin")
#define APP_AUTH_PUBKEY_PATH ("app-auth-pubkey.bin")

grant_protocol_result_t grant_storage_read_request(grant_request_t* request);
grant_protocol_result_t grant_storage_write_request(const grant_request_t* request);
grant_protocol_result_t grant_storage_delete_request(void);

// App auth pubkey storage functions
bool grant_storage_read_app_auth_pubkey(uint8_t* pubkey);
bool grant_storage_write_app_auth_pubkey(const uint8_t* pubkey);
bool grant_storage_app_auth_pubkey_exists(void);
bool grant_storage_delete_app_auth_pubkey(void);
