#pragma once

#include <stdbool.h>
#include <stdint.h>

// TODO: Do some experiments to figure out the best size for this, and
// Mlc, Mle, and the internal ST RFAL buffers (e.g. RFAL_FEATURE_ISO_DEP_APDU_MAX_LEN).
// Note that this needs to be bigger than Mlc/Mle + overhead.
#define T4T_BUF_LEN (150)

#define T4T_CLA (0x00)

// NFC Type 4 Tag protocol functions.
bool t4t_handle_command(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len);
bool t4t_is_valid(uint8_t* cmd, uint32_t cmd_len);
