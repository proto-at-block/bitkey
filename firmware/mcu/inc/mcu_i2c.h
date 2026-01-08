#pragma once

#include "mcu_gpio.h"
#include "perf.h"
#include "rtos.h"

#include <stdint.h>

typedef enum {
  MCU_I2C_STATE_START_ADDR_SEND,     /* Send start + (first part of) address */
  MCU_I2C_STATE_ADDR_WF_ACKNACK,     /* Wait for ACK/NACK on (the first part of) address */
  MCU_I2C_STATE_ADDR_WF_2ND_ACKNACK, /* Wait for ACK/NACK on the second part of a 10 bit address */
  MCU_I2C_STATE_R_START_ADDR_SEND,   /* Send a repeated start + (first part of) address */
  MCU_I2C_STATE_R_ADDR_WF_ACKNACK, /* Wait for ACK/NACK on an address sent after a repeated start*/
  MCU_I2C_STATE_DATA_SEND,         /* Send data */
  MCU_I2C_STATE_DATA_WF_ACKNACK,   /* Wait for ACK/NACK on data sent */
  MCU_I2C_STATE_WF_DATA,           /* Wait for data */
  MCU_I2C_STATE_WF_STOP_SENT,      /* Wait for STOP to have been transmitted */
  MCU_I2C_STATE_DONE,              /* Transfer completed successfully */

  /* Below two states are only for the `opt` transfer functions */
  MCU_I2C_STATE_WF_TRANSFER_RESUME,
  MCU_I2C_STATE_WF_RX_BEGIN,
  MCU_I2C_STATE_ERROR,
} mcu_i2c_transfer_state_t;

typedef enum {
  /* In progress code (>0) */
  MCU_I2C_TRANSFER_IN_PROGRESS = 1, /* Transfer in progress. */

  /* Complete code (=0) */
  MCU_I2C_TRANSFER_DONE = 0, /* Transfer completed successfully. */

  /* Transfer error codes (<0). */
  MCU_I2C_TRANSFER_NACK = -1,        /* NACK received during transfer. */
  MCU_I2C_TRANSFER_BUS_ERR = -2,     /* Bus error during transfer (misplaced START/STOP). */
  MCU_I2C_TRANSFER_ARB_LOST = -3,    /* Arbitration lost during transfer. */
  MCU_I2C_TRANSFER_USAGE_FAULT = -4, /* Usage fault. */
  MCU_I2C_TRANSFER_SW_FAULT = -5,    /* SW fault. */
  MCU_I2C_TRANSFER_TIMEOUT = -6,     /* Transfer timeout. */
} mcu_i2c_err_t;

typedef enum {
  MCU_I2C_FLAG_WRITE = 0x0001, /* Indicate plain write sequence: S+ADDR(W)+DATA0+P */
  MCU_I2C_FLAG_READ = 0x0002,  /* Indicate plain read sequence: S+ADDR(R)+DATA0+P */
  MCU_I2C_FLAG_WRITE_READ =
    0x0004, /* Indicate combined write/read sequence: S+ADDR(W)+DATA0+Sr+ADDR(R)+DATA1+P */
  MCU_I2C_FLAG_WRITE_WRITE =
    0x0008, /* Indicate write sequence using two buffers: S+ADDR(W)+DATA0+DATA1+P */
  MCU_I2C_FLAG_10BIT_ADDR = 0x0010, /* Use 10 bit address. */
} mcu_i2c_flags_t;

typedef enum {
  MCU_I2C0 = 0,
  MCU_I2C1 = 1,
} mcu_i2c_t;

typedef enum {
  MCU_I2C_FREQ_100K,
  MCU_I2C_FREQ_400K,
  MCU_I2C_FREQ_MAX,
} mcu_i2c_freq_t;

typedef struct {
  /** Flags defining sequence type and details, see MCU_I2C_FLAG_ defines. */
  mcu_i2c_flags_t flags;

  /**
   * Buffers used to hold data to send from or receive into, depending
   * on sequence type.
   */
  struct {
    /** Buffer used for data to transmit/receive, must be @p len long. */
    uint8_t* data;

    /**
     * Number of bytes in @p data to send or receive. Notice that when
     * receiving data to this buffer, at least 1 byte must be received.
     * Setting @p len to 0 in the receive case is considered a usage fault.
     * Transmitting 0 bytes is legal, in which case only the address
     * is transmitted after the start condition.
     */
    uint16_t len;
  } buf[2];
} mcu_i2c_transfer_seq_t;

typedef struct {
  mcu_gpio_config_t sda;
  mcu_gpio_config_t scl;
  mcu_i2c_t peripheral;
} mcu_i2c_bus_config_t;

typedef struct {
  mcu_i2c_t peripheral;
  uint16_t addr;
  mcu_i2c_freq_t freq;
} mcu_i2c_device_t;

void mcu_i2c_init(void);
void mcu_i2c_bus_init(const mcu_i2c_bus_config_t* config, const mcu_i2c_device_t* device,
                      const bool enable);
void mcu_i2c_set_bus_freq(const mcu_i2c_device_t* device);
mcu_i2c_err_t mcu_i2c_transfer(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                               const uint32_t timeout_ms);

bool mcu_i2c_transfer_enter_critical(const mcu_i2c_device_t* device);
bool mcu_i2c_transfer_exit_critical(const mcu_i2c_device_t* device);
mcu_i2c_err_t mcu_i2c_transfer_critical(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                                        const uint32_t timeout_ms);
