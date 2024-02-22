#include "t4t.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "log.h"
#include "t4t_impl.h"

#include <string.h>

t4t_priv_t t4t_priv = {
  .cc_file =
    {
      .cc_len = htons(0x000f),
      .t4t_vno = 0x20,
      .mle = htons(0x007f),
      .mlc = htons(0x007f),
      .ndef_file_ctrl =
        {
          .type = 0x04,  // Section 4.7.3
          .length = 0x06,
          .ndef_file_id = htons(NDEF_FID),
          .ndef_file_size = htons(FAKE_NDEF_SIZE),
          .read_access_condition = 0,
          .write_access_condition = 0,
        },
    },
  .ndef_file = {0x00, 0x00,                   /* NDEF length                 */
                0xD1,                         /* NDEF Header                 */
                0x01,                         /* NDEF type length            */
                0x11,                         /* NDEF payload length         */
                0x55,                         /* NDEF Type                   */
                0x01,                         /* NDEF URI abbreviation field */
                0x73, 0x74, 0x2E, 0x63, 0x6F, /* NDEF URI string             */
                0x6D, 0x2F, 0x73, 0x74, 0x32, 0x35, 0x2D, 0x64, 0x65, 0x6D, 0x6F},
  .file_index = FILE_IDX_NONE,
  .file_sizes =
    {
      [FILE_IDX_CC] = sizeof(t4t_priv.cc_file),
      [FILE_IDX_NDEF] = REAL_NDEF_SIZE,
    },
};

uint8_t* get_current_file(void) {
  switch (t4t_priv.file_index) {
    case FILE_IDX_CC:
      return (uint8_t*)&t4t_priv.cc_file;
    case FILE_IDX_NDEF:
      return &t4t_priv.ndef_file[0];
    default:
      ASSERT(false);
  }
}

bool t4t_handle_command(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  ASSERT(*rsp_len >= SW_SIZE);

  if (!t4t_is_valid(cmd, cmd_len)) {
    goto err;
  }

  // if updating this switch, propagate changes to t4t_fuzz.cc
  switch (cmd[INS]) {
    case T4T_INS_SELECT:
      return t4t_select(cmd, cmd_len, rsp, rsp_len);
    case T4T_INS_READ:
      return t4t_read(cmd, cmd_len, rsp, rsp_len);
    case T4T_INS_UPDATE:
      return t4t_update(cmd, cmd_len, rsp, rsp_len);
    case T4T_INS_UPDATE_ODO:
      return t4t_update_odo(cmd, cmd_len, rsp, rsp_len);
    default:
      goto err;
  }

err:
  RSP_UNSUPPORTED_INS(rsp, 0);
  return false;
}

bool t4t_select(uint8_t* cmd, uint32_t UNUSED(cmd_len), uint8_t* rsp, uint32_t* rsp_len) {
  enum { STATE_NONE = 0, STATE_APP_SELECTED, STATE_CC_SELECTED, STATE_NDEF_SELECTED };

  static uint32_t state = STATE_NONE;

  // 5.2 Select Data Commands

  // NDEF application ID
  const uint8_t AID_NDEF[] = {0xD2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01};
  const uint8_t CC_FID_ARR[] = {CC_FID >> 8, CC_FID & 0xFF};
  const uint8_t NDEF_FID_ARR[] = {NDEF_FID >> 8, NDEF_FID & 0xFF};

  const uint16_t data_off = LC + 1;

  // 5.2.1 Select NDEF Tag Application
  if (cmd[P1] == 0x04 && cmd[P2] == 0x00) {
    if (cmd[LC] != 7) {
      // Command data length must be 7 bytes
      goto err;
    }
    if (memcmp(&cmd[data_off], AID_NDEF, sizeof(AID_NDEF)) != 0) {
      // Data must be exactly as encoded in AID_NDEF
      goto err;
    }
    state = STATE_APP_SELECTED;
    goto ok;
  } else if (state >= STATE_APP_SELECTED) {
    // NDEF application must be selected before CC or NDEF files may be selected.

    if (cmd[P1] != 0x00 && cmd[P2] != 0x0C && cmd[LC] != 2) {
      // Selecting both CC or NDEF files requires these specific parameter values,
      // and the length must be 2.
      goto err;
    }

    if (memcmp(&cmd[data_off], CC_FID_ARR, sizeof(CC_FID_ARR)) == 0) {
      // Request to select CC file.
      state = STATE_CC_SELECTED;
      t4t_priv.file_index = FILE_IDX_CC;
      goto ok;
    }
    if (memcmp(&cmd[data_off], NDEF_FID_ARR, sizeof(NDEF_FID_ARR)) == 0) {
      // Request to select NDEF file.
      state = STATE_NDEF_SELECTED;
      t4t_priv.file_index = FILE_IDX_NDEF;
      goto ok;
    }
  } else {
    goto err;
  }

err:
  *rsp_len = SW_SIZE;
  RSP_FILE_NOT_FOUND(rsp, 0);
  state = STATE_NONE;
  LOGD("File not found");
  return true;

ok:
  *rsp_len = SW_SIZE;
  RSP_OK(rsp, 0);
  return true;
}

bool t4t_read(uint8_t* cmd, uint32_t UNUSED(cmd_len), uint8_t* rsp, uint32_t* rsp_len) {
  uint32_t in_len = *rsp_len;
  *rsp_len = SW_SIZE;  // Default to SW_SIZE to catch all errors.

  if (t4t_priv.file_index == FILE_IDX_NONE) {
    // File was never selected.
    RSP_FILE_NOT_FOUND(rsp, 0);
    LOGD("File not found");
    return false;
  }

  uint16_t offset = (cmd[P1] << 8) | cmd[P2];
  size_t num_bytes_to_read = le_to_int(&cmd[LC], false);

  if (num_bytes_to_read > UINT16_MAX) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  if (in_len < (num_bytes_to_read + SW_SIZE)) {
    // Response buffer must be large enough to contain the full response plus two
    // bytes for status words.
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  uint16_t file_size = t4t_priv.file_sizes[t4t_priv.file_index];
  if (file_size < offset) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }
  num_bytes_to_read = BLK_MIN((uint32_t)file_size - offset, num_bytes_to_read);

  if (num_bytes_to_read + SW_SIZE > in_len) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  uint8_t* file = get_current_file();
  memcpy(rsp, &file[offset], num_bytes_to_read);
  *rsp_len = num_bytes_to_read + SW_SIZE;

  RSP_OK(rsp, num_bytes_to_read);
  return true;
}

bool t4t_update(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  *rsp_len = SW_SIZE;

  uint16_t offset = (cmd[P1] << 8) | cmd[P2];
  const uint16_t data_off = LC + 1;

  uint8_t num_bytes = cmd[LC];

  if (t4t_priv.file_index != FILE_IDX_NDEF) {
    RSP_FILE_NOT_FOUND(rsp, 0);
    return false;
  }

  uint16_t file_size = t4t_priv.file_sizes[t4t_priv.file_index];
  if ((offset + num_bytes) > file_size) {
    RSP_FCP_END_OF_FILE(rsp, 0);
    return false;
  }

  uint16_t total_len = num_bytes + data_off;
  if (total_len > cmd_len) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  memcpy(&t4t_priv.ndef_file[offset], &cmd[data_off], num_bytes);
  RSP_OK(rsp, 0);
  return true;
}

bool t4t_update_odo(uint8_t* cmd, uint32_t cmd_len, uint8_t* rsp, uint32_t* rsp_len) {
  *rsp_len = SW_SIZE;

  if (cmd[P1] != 0 || cmd[P2] != 0) {
    RSP_WRONG_PARAMS(rsp, 0);
    return false;
  }

  uint16_t num_bytes = lc_to_int(&cmd[LC]);
  const uint16_t data_off = is_short_coding(num_bytes) ? LC + 1 : LC + 3;
  uint8_t* data = &cmd[data_off];

  // Section 5.3.2
  if (data[0] != 0x54 && data[1] != 0x03) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  uint32_t offset = (data[2] << 16) | (data[3] << 8) | data[4];
  data = &data[5];

  if (t4t_priv.file_index != FILE_IDX_NDEF) {
    RSP_FILE_NOT_FOUND(rsp, 0);
    return false;
  }

  uint16_t file_size = t4t_priv.file_sizes[t4t_priv.file_index];
  if ((offset + num_bytes) > file_size) {
    RSP_FCP_END_OF_FILE(rsp, 0);
    return false;
  }

  uint16_t total_len = num_bytes + data_off;
  if (total_len > cmd_len) {
    RSP_FCI_GENERIC_FAILURE(rsp, 0);
    return false;
  }

  memcpy(&t4t_priv.ndef_file[offset], data, num_bytes);
  RSP_OK(rsp, 0);
  return true;
}

bool t4t_is_valid(uint8_t* cmd, uint32_t cmd_len) {
  return (cmd_len >= (P2 + 1)) && cmd[CLA] == T4T_CLA;
}
