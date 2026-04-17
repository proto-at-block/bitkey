/**
 * @file touch_mfgtest.h
 * @brief Touch IC register access for manufacturing test and debug screens.
 *
 * Provides register definitions and I2C helper functions for the FT3169 touch IC.
 * Used by the on-device debug app and CLI selftest.
 */

#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// =============================================================================
// Working mode registers
// =============================================================================
#define TOUCH_MFGTEST_FW_VERSION_REG 0xA6
#define TOUCH_MFGTEST_VENDOR_ID_REG  0xA8
#define TOUCH_MFGTEST_MODULE_ID_REG  0xA9
#define TOUCH_MFGTEST_POWER_MODE_REG 0xA5

#define TOUCH_MFGTEST_POWER_ACTIVE  0x00
#define TOUCH_MFGTEST_POWER_MONITOR 0x01
#define TOUCH_MFGTEST_POWER_SLEEP   0x03

// =============================================================================
// Mode switching
// =============================================================================
#define TOUCH_MFGTEST_MODE_REG     0x00
#define TOUCH_MFGTEST_FACTORY_MODE 0x40
#define TOUCH_MFGTEST_WORK_MODE    0x00

// =============================================================================
// Factory mode registers
// =============================================================================
#define TOUCH_MFGTEST_SCAN_VALUE  0xC0  // Factory mode + start scan (bit7=1)
#define TOUCH_MFGTEST_RAWDATA_REG 0x36
#define TOUCH_MFGTEST_RAWADDR_REG 0x01
#define TOUCH_MFGTEST_DATASEL_REG 0x06
#define TOUCH_MFGTEST_FREQ_REG    0x0A
#define TOUCH_MFGTEST_TX_NUM_REG  0x02
#define TOUCH_MFGTEST_RX_NUM_REG  0x03

// RawData address selectors
#define TOUCH_MFGTEST_RAWADDR_MC        0xAA  // Mutual capacitance
#define TOUCH_MFGTEST_RAWADDR_SC_NORMAL 0xAB  // Self-cap normal
#define TOUCH_MFGTEST_RAWADDR_SC_WATER  0xAC  // Self-cap waterproof

// =============================================================================
// Capacitance registers (working mode)
// =============================================================================
#define TOUCH_MFGTEST_MCAP_DIFF_REG      0x40  // Mutual cap diff (8x8)
#define TOUCH_MFGTEST_SCAP_WP_DIFF_REG   0x42  // Self-cap waterproof diff
#define TOUCH_MFGTEST_SCAP_NORM_DIFF_REG 0x44  // Self-cap normal diff

// =============================================================================
// Scap CB registers (factory mode)
// =============================================================================
#define TOUCH_MFGTEST_SCAP_CB_MODE_REG 0x44  // 1=waterproof, 0=normal
#define TOUCH_MFGTEST_SCAP_CB_ADDR_H   0x49
#define TOUCH_MFGTEST_SCAP_CB_ADDR_L   0x45
#define TOUCH_MFGTEST_SCAP_CB_DATA_REG 0x4E

// =============================================================================
// MCap CMB registers (factory mode)
// =============================================================================
#define TOUCH_MFGTEST_MCAP_CMB_REG  0x3A
#define TOUCH_MFGTEST_MCAP_CMB_CLR1 0x24
#define TOUCH_MFGTEST_MCAP_CMB_CLR2 0x26

// =============================================================================
// Grid dimensions (FT3169 on W3)
// =============================================================================
#define TOUCH_MFGTEST_TX_NUM            8
#define TOUCH_MFGTEST_RX_NUM            8
#define TOUCH_MFGTEST_MCAP_ROWS         TOUCH_MFGTEST_TX_NUM
#define TOUCH_MFGTEST_MCAP_COLS         TOUCH_MFGTEST_RX_NUM
#define TOUCH_MFGTEST_MCAP_SIZE         (TOUCH_MFGTEST_MCAP_ROWS * TOUCH_MFGTEST_MCAP_COLS * 2)
#define TOUCH_MFGTEST_SCAP_COLS         8
#define TOUCH_MFGTEST_SCAP_ROWS_PER_REG 2
#define TOUCH_MFGTEST_SCAP_SIZE         (TOUCH_MFGTEST_SCAP_COLS * TOUCH_MFGTEST_SCAP_ROWS_PER_REG * 2)

// Data sizes for collect/selftest reads
#define TOUCH_MFGTEST_MCAP_RAW_SIZE 128  // 8x8x2
#define TOUCH_MFGTEST_SCAP_CB_SIZE  32   // (Tx+Rx)*2
#define TOUCH_MFGTEST_MCAP_CMB_SIZE 64   // Tx*Rx*1
#define TOUCH_MFGTEST_SCAP_RAW_SIZE 32   // (Tx+Rx)*2

// =============================================================================
// I2C helper functions
// =============================================================================

/**
 * @brief Write a single byte to a touch IC register.
 */
bool touch_mfgtest_write_reg(uint8_t reg, uint8_t value);

/**
 * @brief Read a single byte from a touch IC register.
 */
bool touch_mfgtest_read_reg(uint8_t reg, uint8_t* value);

/**
 * @brief Read multiple bytes from a touch IC register.
 */
bool touch_mfgtest_read_buf(uint8_t reg, uint8_t* buf, size_t len);

/**
 * @brief Write multiple bytes to a touch IC register.
 */
bool touch_mfgtest_write_buf(uint8_t reg, uint8_t* buf, size_t len);
