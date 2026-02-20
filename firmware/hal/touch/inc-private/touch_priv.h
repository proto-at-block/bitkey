/**
 * @file touch_priv.h
 *
 * @brief Touch HAL Private Types and Functions
 *
 * Shared private types and functions for touch HAL implementation files.
 *
 * @{
 */

#pragma once

#include "exti.h"
#include "touch.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Touch event circular buffer size.
 * Must be a power of 2 for efficient modulo operations.
 */
#define TOUCH_EVENT_BUFFER_SIZE 16
_Static_assert((TOUCH_EVENT_BUFFER_SIZE & (TOUCH_EVENT_BUFFER_SIZE - 1)) == 0,
               "TOUCH_EVENT_BUFFER_SIZE must be a power of 2");

/**
 * @brief Touch event circular buffer structure.
 */
typedef struct {
  touch_event_t events[TOUCH_EVENT_BUFFER_SIZE];
  uint8_t head;  /**< Next write position. */
  uint8_t tail;  /**< Next read position. */
  uint8_t count; /**< Number of events in buffer. */
} touch_event_buffer_t;

/**
 * @brief Private touch state structure.
 */
typedef struct {
  touch_config_t config;             /**< Touch configuration. */
  exti_config_t exti_config;         /**< Interrupt configuration. */
  uint8_t flow_work_cnt_last;        /**< Last flow work counter value. */
  uint8_t flow_work_hold_cnt;        /**< Number of consecutive identical flow counts. */
  touch_event_t last_touch_event;    /**< Cached coordinates for active touch. */
  touch_event_buffer_t event_buffer; /**< Circular buffer for touch events. */
  uint32_t last_esd_check_ms;        /**< Timestamp of last ESD check. */
  bool fwup_in_progress;             /**< True when firmware upgrade is running. */
} touch_priv_t;

/**
 * @brief Global touch private state.
 */
extern touch_priv_t _touch_priv;

/**
 * @brief Write data to an I2C register.
 *
 * @param reg_addr Register address to write to.
 * @param tx_buf   Data buffer to write.
 * @param tx_len   Length of data to write.
 *
 * @return true on success, false on failure.
 */
bool touch_i2c_write(uint8_t reg_addr, uint8_t* tx_buf, size_t tx_len);

/**
 * @brief Read data from an I2C register.
 *
 * @param reg_addr Register address to read from.
 * @param rx_buf   Buffer to store read data.
 * @param rx_len   Number of bytes to read.
 *
 * @return true on success, false on failure.
 */
bool touch_i2c_read(uint8_t reg_addr, uint8_t* rx_buf, size_t rx_len);

/**
 * @brief Write a single byte to an I2C register.
 *
 * @param reg_addr Register address to write to.
 * @param value    Value to write.
 *
 * @return true on success, false on failure.
 */
bool touch_write_reg(uint8_t reg_addr, uint8_t value);

/**
 * @brief Read a single byte from an I2C register.
 *
 * @param reg_addr Register address to read from.
 * @param value    Pointer to store read value.
 *
 * @return true on success, false on failure.
 */
bool touch_read_reg(uint8_t reg_addr, uint8_t* value);

/**
 * @brief Perform hardware reset of the touch controller.
 *
 * @return true on success, false on failure.
 */
bool touch_hw_reset(void);

/** @} */
