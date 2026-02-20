/**
 * @file touch_internal.h
 *
 * @brief Internal touch driver functions shared between touch.c and touch_fwup.c.
 *
 * @details This header exposes internal I2C communication functions for use by
 * the firmware upgrade module. These functions should not be used outside of
 * the touch HAL implementation.
 */

#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Write data to the touch controller via I2C.
 *
 * @param reg_addr Register/command address.
 * @param tx_buf   Data buffer to write (can be NULL for command-only writes).
 * @param tx_len   Number of data bytes to write.
 *
 * @return true on success, false on failure.
 */
bool touch_i2c_write(uint8_t reg_addr, uint8_t* tx_buf, size_t tx_len);

/**
 * @brief Read data from the touch controller via I2C.
 *
 * @param reg_addr Register/command address.
 * @param rx_buf   Buffer to store read data.
 * @param rx_len   Number of bytes to read.
 *
 * @return true on success, false on failure.
 */
bool touch_i2c_read(uint8_t reg_addr, uint8_t* rx_buf, size_t rx_len);

/**
 * @brief Write a single byte to a touch controller register.
 *
 * @param reg_addr Register address.
 * @param value    Value to write.
 *
 * @return true on success, false on failure.
 */
bool touch_write_reg(uint8_t reg_addr, uint8_t value);

/**
 * @brief Read a single byte from a touch controller register.
 *
 * @param reg_addr Register address.
 * @param value    Pointer to store read value.
 *
 * @return true on success, false on failure.
 */
bool touch_read_reg(uint8_t reg_addr, uint8_t* value);

/**
 * @brief Write a command byte to the touch controller (no data).
 *
 * @param cmd Command byte to write.
 *
 * @return true on success, false on failure.
 */
bool touch_i2c_write_cmd(uint8_t cmd);
