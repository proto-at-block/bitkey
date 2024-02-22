#include "criterion_test_utils.h"
#include "fff.h"
#include "t4t.h"
#include "t4t_impl.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

extern t4t_priv_t t4t_priv;

void select_ndef_app(void) {
  uint8_t select_cmd[] = {0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2, 0x76,
                          0x00, 0x00, 0x85, 0x01, 0x01, 0x00};
  uint8_t select_rsp[SW_SIZE] = {0};
  uint32_t select_rsp_len = SW_SIZE;
  t4t_select(select_cmd, sizeof(select_cmd), select_rsp, &select_rsp_len);
}

void select_file(int file_index) {
  uint8_t select_file_cmd[] = {0x00, 0xA4, 0x00, 0x0C, 0x02, 0x00, 0x00};
  const uint8_t CC_FID_ARR[] = {CC_FID >> 8, CC_FID & 0xFF};
  const uint8_t NDEF_FID_ARR[] = {NDEF_FID >> 8, NDEF_FID & 0xFF};

  uint8_t select_rsp[SW_SIZE] = {0};
  uint32_t select_rsp_len = SW_SIZE;

  if (file_index == FILE_IDX_CC) {
    memcpy(&select_file_cmd[5], CC_FID_ARR, 2);
  } else {
    memcpy(&select_file_cmd[5], NDEF_FID_ARR, 2);
  }

  t4t_select(select_file_cmd, sizeof(select_file_cmd), select_rsp, &select_rsp_len);
}

void select_cc_file(void) {
  select_ndef_app();
  select_file(FILE_IDX_CC);
}

void select_ndef_file(void) {
  select_ndef_app();
  select_file(FILE_IDX_NDEF);
}

Test(t4t, select) {
  cr_assert(t4t_priv.file_index == FILE_IDX_NONE);
  select_cc_file();
  cr_assert(t4t_priv.file_index == FILE_IDX_CC);
  select_ndef_file();
  cr_assert(t4t_priv.file_index == FILE_IDX_NDEF);
}

Test(t4t, select_bad_file) {
  cr_assert(t4t_priv.file_index == FILE_IDX_NONE);
  select_cc_file();

  uint8_t select_file_cmd[] = {0x00, 0xA4, 0x00, 0x0C, 0x02, 0x00, 0x00};
  uint8_t select_rsp[SW_SIZE] = {0};
  uint32_t select_rsp_len = SW_SIZE;

  uint8_t expected_rsp[SW_SIZE] = {0};
  RSP_FILE_NOT_FOUND(expected_rsp, 0);

  t4t_select(select_file_cmd, sizeof(select_file_cmd), select_rsp, &select_rsp_len);
  cr_util_cmp_buffers(expected_rsp, select_rsp, SW_SIZE);
}

Test(t4t, read_cc_file) {
  select_cc_file();
  uint8_t read_file_cmd[] = {
    0x00,  // CLA
    0xB0,  // INS
    0x00,  // P1
    0x00,  // P2
    0x0F,  // Requested response length
  };

  uint8_t read_rsp[T4T_BUF_LEN] = {0};
  uint32_t read_rsp_len = sizeof(read_rsp);

  t4t_read(read_file_cmd, sizeof(read_file_cmd), read_rsp, &read_rsp_len);
  cr_assert(read_rsp_len == sizeof(t4t_priv.cc_file) + SW_SIZE);

  // Check actual buffer
  cr_util_cmp_buffers(&t4t_priv.cc_file, &read_rsp[0], read_rsp_len - SW_SIZE);

  // Check status words
  uint8_t expected_rsp[SW_SIZE] = {0};
  RSP_OK(expected_rsp, 0);
  cr_util_cmp_buffers(&expected_rsp, &read_rsp[read_rsp_len - SW_SIZE], SW_SIZE);
}

Test(t4t, try_read_no_select) {
  uint8_t read_file_cmd[] = {0x00, 0xB0, 0x00, 0x00, 0xFF};

  uint8_t read_rsp[T4T_BUF_LEN] = {0};
  uint32_t read_rsp_len = sizeof(read_rsp);

  t4t_read(read_file_cmd, sizeof(read_file_cmd), read_rsp, &read_rsp_len);

  uint8_t expected_rsp[SW_SIZE] = {0};
  RSP_FILE_NOT_FOUND(expected_rsp, 0);
  cr_util_cmp_buffers(&expected_rsp, &read_rsp, SW_SIZE);
}

Test(t4t, write_read_ndef) {
  select_ndef_file();

  // Write
  uint8_t update_file_cmd[] = {0x00, 0xD6, 0x03, 0xE8, 0x04, 0x01, 0x02, 0x03, 0x04};
  uint8_t update_rsp[T4T_BUF_LEN] = {0};
  uint32_t update_rsp_len = sizeof(update_rsp);

  t4t_update(update_file_cmd, sizeof(update_file_cmd), update_rsp, &update_rsp_len);

  // Manually check NDEF contents
  uint8_t* ndef = get_current_file();
  uint8_t* ndef_off = &ndef[0x03e8];
  uint8_t expected_data[4] = {1, 2, 3, 4};
  cr_util_cmp_buffers(expected_data, ndef_off, 4);

  // Read
  uint8_t read_file_cmd[] = {0x00, 0xB0, 0x03, 0xE8, 0x04};

  uint8_t read_rsp[T4T_BUF_LEN] = {0};
  uint32_t read_rsp_len = sizeof(read_rsp);

  t4t_read(read_file_cmd, sizeof(read_file_cmd), read_rsp, &read_rsp_len);
  cr_assert(read_rsp_len == sizeof(expected_data) + SW_SIZE);

  // Check actual buffer
  cr_util_cmp_buffers(expected_data, &read_rsp[0], read_rsp_len - SW_SIZE);

  // Check status words
  uint8_t expected_rsp[SW_SIZE] = {0};
  RSP_OK(expected_rsp, 0);
  cr_util_cmp_buffers(&expected_rsp, &read_rsp[read_rsp_len - SW_SIZE], SW_SIZE);
}

Test(t4t, cc_file) {
  uint8_t expected_cc_file[] = {
    0x00,
    0x0F, /* CCLEN      */
    0x20, /* T4T_VNo    */
    0x00,
    0x7F, /* MLe        */
    0x00,
    0x7F, /* MLc        */
    0x04, /* T          */
    0x06, /* L          */
    (NDEF_FID & 0xFF00) >> 8,
    (NDEF_FID & 0x00FF), /* V1         */
    (FAKE_NDEF_SIZE & 0xFF00) >> 8,
    (FAKE_NDEF_SIZE & 0x00FF), /* V2         */
    0x00,                      /* V3         */
    0x00                       /* V4         */
  };

  cr_assert(sizeof(t4t_priv.cc_file) == sizeof(expected_cc_file));
  cr_util_cmp_buffers(expected_cc_file, &t4t_priv.cc_file, sizeof(expected_cc_file));
}
