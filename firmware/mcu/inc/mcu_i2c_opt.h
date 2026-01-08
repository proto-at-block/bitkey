#pragma once

#include "mcu_i2c.h"

typedef struct {
  uint8_t* buf;
  uint16_t len;
  bool last;
  bool tx_only;
} mcu_i2c_transfer_opt_seq_t;

// Alternative MCU transceive functions. Avoid using these unless needed!
// These functions expose different options than the standard functions from mcu_i2c.h
// The caller has more direct control over sending the STOP bit via the 'last' argument.
mcu_i2c_transfer_state_t mcu_i2c_transfer_opt_init(const mcu_i2c_device_t* device,
                                                   mcu_i2c_transfer_opt_seq_t* seq);
mcu_i2c_transfer_state_t mcu_i2c_transfer_opt(const mcu_i2c_device_t* device,
                                              mcu_i2c_transfer_opt_seq_t* seq);
