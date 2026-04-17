/**
 * @file touch_mfgtest.c
 * @brief Touch IC register access for manufacturing test and debug screens.
 */

#include "touch_mfgtest.h"

#include "touch_priv.h"

bool touch_mfgtest_write_reg(uint8_t reg, uint8_t value) {
  return touch_i2c_write(reg, &value, 1);
}

bool touch_mfgtest_read_reg(uint8_t reg, uint8_t* value) {
  return touch_i2c_read(reg, value, 1);
}

bool touch_mfgtest_read_buf(uint8_t reg, uint8_t* buf, size_t len) {
  return touch_i2c_read(reg, buf, len);
}

bool touch_mfgtest_write_buf(uint8_t reg, uint8_t* buf, size_t len) {
  return touch_i2c_write(reg, buf, len);
}
