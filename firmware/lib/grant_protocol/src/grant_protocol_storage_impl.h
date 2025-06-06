#pragma once

#include "grant_protocol.h"

#include <stdbool.h>
#include <stdint.h>

#define GRANT_REQUEST_PATH ("grant-request.bin")

grant_protocol_result_t grant_storage_read_request(grant_request_t* request);
grant_protocol_result_t grant_storage_write_request(const grant_request_t* request);
grant_protocol_result_t grant_storage_delete_request(void);
