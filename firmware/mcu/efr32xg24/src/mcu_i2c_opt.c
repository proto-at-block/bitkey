
#include "mcu_i2c_opt.h"

#include "assert.h"
#include "mcu_i2c_priv.h"

#include "em_device.h"
#include "em_i2c.h"

#include <stdint.h>

typedef struct {
  mcu_i2c_transfer_state_t state;
  mcu_i2c_err_t result;
  uint16_t offset;
} mcu_i2c_transfer_opt_t;

static mcu_i2c_transfer_opt_t PERIPHERALS_DATA opt_priv[1];  // Only supported for I2C1
static bool PERIPHERALS_DATA bus_enabled = false;

static bool opt_handle_state_wf_stop_sent(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_start_addr_send(mcu_i2c_bus_t* bus, const mcu_i2c_device_t* device,
                                             mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_wf_ack_nack(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_r_start_addr_send(mcu_i2c_bus_t* bus, const mcu_i2c_device_t* device,
                                               mcu_i2c_transfer_opt_seq_t* seq,
                                               mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_r_addr_wf_ack_nack(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                                mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_data_send(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                       mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_data_wf_ack_nack(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_t* transfer);
static bool opt_handle_state_wf_data(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                     mcu_i2c_transfer_opt_t* transfer);
static void enable_bus_once(const mcu_i2c_device_t* device);

mcu_i2c_transfer_state_t mcu_i2c_transfer_opt_init(const mcu_i2c_device_t* device,
                                                   mcu_i2c_transfer_opt_seq_t* seq) {
  ASSERT(seq);

  mcu_i2c_bus_t* bus = _mcu_i2c_handle(device->peripheral);
  ASSERT(bus == I2C1);

  mcu_i2c_transfer_opt_t* transfer = &opt_priv[0];

  transfer->state = MCU_I2C_STATE_START_ADDR_SEND;
  transfer->result = MCU_I2C_TRANSFER_IN_PROGRESS;
  transfer->offset = 0;

  _mcu_i2c_bus_init_lock(device->peripheral);

  /* Set bus frequency */
  enable_bus_once(device);

  if (!_mcu_i2c_transfer_init(bus)) {
    _mcu_i2c_bus_init_unlock(device->peripheral);
    return MCU_I2C_STATE_ERROR;
  }

  mcu_i2c_transfer_state_t result = mcu_i2c_transfer_opt(device, seq);
  _mcu_i2c_bus_init_unlock(device->peripheral);

  return result;
}

mcu_i2c_transfer_state_t mcu_i2c_transfer_opt(const mcu_i2c_device_t* device,
                                              mcu_i2c_transfer_opt_seq_t* seq) {
  bool finished = false;

  mcu_i2c_bus_t* bus = _mcu_i2c_handle(device->peripheral);
  ASSERT(bus == I2C1);

  mcu_i2c_transfer_opt_t* transfer = &opt_priv[0];

  _mcu_i2c_bus_lock(device->peripheral);

  while (!finished) {
    uint32_t pending = bus->IF;

    if (pending & I2C_IF_ERRORS) {
      if (pending & I2C_IF_ARBLOST) {
        transfer->result = MCU_I2C_TRANSFER_ARB_LOST;
      } else if (pending & I2C_IF_BUSERR) {
        transfer->result = MCU_I2C_TRANSFER_BUS_ERR;
      }
      transfer->state = MCU_I2C_STATE_DONE;
      break;
    }

    switch (transfer->state) {
      case MCU_I2C_STATE_START_ADDR_SEND:
        finished = opt_handle_state_start_addr_send(bus, device, transfer);
        break;

      case MCU_I2C_STATE_ADDR_WF_ACKNACK:
        finished = opt_handle_state_wf_ack_nack(bus, transfer);
        if (!finished) {
          continue;
        }
        break;

      case MCU_I2C_STATE_R_START_ADDR_SEND:
        finished = opt_handle_state_r_start_addr_send(bus, device, seq, transfer);
        break;

      case MCU_I2C_STATE_R_ADDR_WF_ACKNACK:
        finished = opt_handle_state_r_addr_wf_ack_nack(bus, seq, transfer);
        if (!finished) {
          continue;
        }
        break;

      case MCU_I2C_STATE_WF_RX_BEGIN:
        transfer->state = MCU_I2C_STATE_R_START_ADDR_SEND;
        continue;

      case MCU_I2C_STATE_DATA_SEND:
      case MCU_I2C_STATE_WF_TRANSFER_RESUME:
        finished = opt_handle_state_data_send(bus, seq, transfer);
        break;

      case MCU_I2C_STATE_DATA_WF_ACKNACK:
        finished = opt_handle_state_data_wf_ack_nack(bus, transfer);
        if (!finished) {
          continue;
        }
        break;

      case MCU_I2C_STATE_WF_DATA:
        finished = opt_handle_state_wf_data(bus, seq, transfer);
        break;

      case MCU_I2C_STATE_WF_STOP_SENT:
        finished = opt_handle_state_wf_stop_sent(bus, transfer);
        break;

      default:
        transfer->result = MCU_I2C_TRANSFER_SW_FAULT;
        transfer->state = MCU_I2C_STATE_DONE;
        finished = true;
        break;
    }
  }

  if (transfer->state == MCU_I2C_STATE_DONE) {
    // Disable interrupt sources when done.
    bus->IEN = 0;

    // Update the result unless a fault has already occurred.
    if (transfer->result == MCU_I2C_TRANSFER_IN_PROGRESS) {
      transfer->result = MCU_I2C_TRANSFER_DONE;
    }

    _mcu_i2c_bus_unlock(device->peripheral);
    return MCU_I2C_STATE_DONE;
  }

  _mcu_i2c_bus_unlock(device->peripheral);
  return transfer->state;
}

static bool opt_handle_state_wf_stop_sent(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_t* transfer) {
  uint32_t pending = bus->IF;
  if (pending & I2C_IF_MSTOP) {
    _mcu_i2c_int_clear(bus, I2C_IF_MSTOP);
    transfer->state = MCU_I2C_STATE_DONE;
  }
  return true;
}

static bool opt_handle_state_start_addr_send(mcu_i2c_bus_t* bus, const mcu_i2c_device_t* device,
                                             mcu_i2c_transfer_opt_t* transfer) {
  uint32_t tmp = (uint32_t)(device->addr) & 0xfe;
  transfer->state = MCU_I2C_STATE_ADDR_WF_ACKNACK;
  bus->TXDATA = tmp; /* Data not transmitted until the START is sent. */
  bus->CMD = I2C_CMD_START;
  return true;
}

static bool opt_handle_state_wf_ack_nack(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_t* transfer) {
  uint32_t pending = bus->IF;
  if (pending & I2C_IF_NACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_NACK);
    transfer->result = MCU_I2C_TRANSFER_NACK;
    transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
    bus->CMD = I2C_CMD_STOP;
  } else if (pending & I2C_IF_ACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_ACK);
    transfer->state = MCU_I2C_STATE_DATA_SEND;
    return false;  // Don't set finished = true yet.
  }
  return true;
}

static bool opt_handle_state_r_start_addr_send(mcu_i2c_bus_t* bus, const mcu_i2c_device_t* device,
                                               mcu_i2c_transfer_opt_seq_t* seq,
                                               mcu_i2c_transfer_opt_t* transfer) {
  uint32_t tmp = (uint32_t)(device->addr & 0xfe);

  // If this is a write+read combined sequence, read is about to start.
  if (!seq->tx_only) {
    // Indicate a read request.
    tmp |= 1;
    // If reading only one byte, prepare the NACK now before START command.
    if (seq->len == 1) {
      bus->CMD = I2C_CMD_NACK;
    }
  }

  transfer->state = MCU_I2C_STATE_R_ADDR_WF_ACKNACK;
  // The START command has to be written first since repeated start. Otherwise,
  // data would be sent first.
  bus->CMD = I2C_CMD_START;
  bus->TXDATA = tmp;
  return true;
}

static bool opt_handle_state_r_addr_wf_ack_nack(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                                mcu_i2c_transfer_opt_t* transfer) {
  uint32_t pending = bus->IF;
  if (pending & I2C_IF_NACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_NACK);
    transfer->result = MCU_I2C_TRANSFER_NACK;
    transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
    bus->CMD = I2C_CMD_STOP;
  } else if (pending & I2C_IF_ACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_ACK);

    /* Determine whether receiving or sending data. */
    if (!seq->tx_only) {
      transfer->state = MCU_I2C_STATE_WF_DATA;
      // Ready to receive. Exit the state machine now.
      return true;
    } else {
      transfer->state = MCU_I2C_STATE_DATA_SEND;
      return false;  // Immediately begin data send.
    }
  }
  return true;
}

static bool opt_handle_state_data_send(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                       mcu_i2c_transfer_opt_t* transfer) {
  if (transfer->offset < seq->len) {
    bus->TXDATA = seq->buf[transfer->offset++];
    transfer->state = MCU_I2C_STATE_DATA_WF_ACKNACK;
    return true;
  }

  // Reached end of data buffer.
  transfer->offset = 0;

  if (seq->last) {
    if (seq->tx_only) {
      transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
      bus->CMD = I2C_CMD_STOP;
    } else {
      transfer->state = MCU_I2C_STATE_WF_RX_BEGIN;
    }
  } else {
    transfer->state = MCU_I2C_STATE_WF_TRANSFER_RESUME;
  }
  return true;
}

static bool opt_handle_state_data_wf_ack_nack(mcu_i2c_bus_t* bus,
                                              mcu_i2c_transfer_opt_t* transfer) {
  uint32_t pending = bus->IF;
  if (pending & I2C_IF_NACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_NACK);
    transfer->result = MCU_I2C_TRANSFER_NACK;
    transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
    bus->CMD = I2C_CMD_STOP;
  } else if (pending & I2C_IF_ACK) {
    _mcu_i2c_int_clear(bus, I2C_IF_ACK);
    transfer->state = MCU_I2C_STATE_DATA_SEND;
    return false;
  }
  return true;
}

static bool opt_handle_state_wf_data(mcu_i2c_bus_t* bus, mcu_i2c_transfer_opt_seq_t* seq,
                                     mcu_i2c_transfer_opt_t* transfer) {
  uint32_t pending = bus->IF;
  if (pending & I2C_IF_RXDATAV) {
    unsigned int rx_len = seq->len;

    // Must read out data not to block further progress.
    uint8_t data = (uint8_t)(bus->RXDATA);

#if (defined(_SILICON_LABS_32B_SERIES_2_CONFIG_1) || \
     defined(_SILICON_LABS_32B_SERIES_2_CONFIG_2) || defined(_SILICON_LABS_32B_SERIES_2_CONFIG_3))
    // Errata I2C_E303. I2C Fails to Indicate New Incoming Data.
    uint32_t status = bus->STATUS;
    // look for invalid RXDATAV = 0 and RXFULL = 1 condition
    if (((status & I2C_IF_RXDATAV) == 0) & ((status & I2C_IF_RXFULL) != 0)) {
      // Performing a dummy read of the RXFIFO (I2C_RXDATA).
      // This restores the expected RXDATAV = 1 and RXFULL = 0 condition.
      (void)bus->RXDATA;
      // The dummy read will also set the RXUFIF flag bit, which should be ignored and
      // cleared.
      _mcu_i2c_int_clear(bus, I2C_IF_RXUF);
    }
#endif

    // SW needs to clear RXDATAV IF on Series 2 devices.
    // Flag is kept high by HW if buffer is not empty.
#if defined(_SILICON_LABS_32B_SERIES_2)
    _mcu_i2c_int_clear(bus, I2C_IF_RXDATAV);
#endif

    // Make sure that there is no storing beyond the end of the buffer (just in case).
    if (transfer->offset < rx_len) {
      seq->buf[transfer->offset++] = data;
    }

    // If all requested data is read, the sequence should end. */
    if (transfer->offset >= rx_len) {
      transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
      bus->CMD = I2C_CMD_STOP;
    } else {
      // Send ACK and wait for the next byte. */
      bus->CMD = I2C_CMD_ACK;

      if ((1 < rx_len) && (transfer->offset == (rx_len - 1))) {
        // If receiving more than one byte and this is the next
        // to last byte, transmit the NACK now before receiving
        // the last byte
        bus->CMD = I2C_CMD_NACK;
      }
    }
  }
  return true;
}

static void enable_bus_once(const mcu_i2c_device_t* device) {
  if (!bus_enabled) {
    mcu_i2c_set_bus_freq(device);
  }
  bus_enabled = true;
}
