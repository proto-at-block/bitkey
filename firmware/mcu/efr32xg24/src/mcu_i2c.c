#include "mcu_i2c.h"

#include "assert.h"
#include "mcu_i2c_priv.h"

#include "em_bus.h"
#include "em_cmu.h"
#include "em_device.h"
#include "em_i2c.h"

#include <stdint.h>

// Code based on SiLab's `em_i2c.c`.

#if (I2C_COUNT == 1)
#define I2C_REF_VALID(ref) ((ref) == I2C0)
#elif (I2C_COUNT == 2)
#define I2C_REF_VALID(ref) (((ref) == I2C0) || ((ref) == I2C1))
#elif (I2C_COUNT == 3)
#define I2C_REF_VALID(ref) (((ref) == I2C0) || ((ref) == I2C1) || ((ref) == I2C2))
#endif

// Maximum I2C transmission rate
#if defined(_SILICON_LABS_32B_SERIES_0)
#define I2C_CR_MAX 4
#elif defined(_SILICON_LABS_32B_SERIES_1)
#define I2C_CR_MAX 8
#elif defined(_SILICON_LABS_32B_SERIES_2)
#define I2C_CR_MAX 8
#else
#warning "Max I2C transmission rate constant is not defined"
#endif

#define MCU_I2C0_CLOCK cmuClock_I2C0
#define MCU_I2C1_CLOCK cmuClock_I2C1

typedef struct {
  mcu_i2c_transfer_state_t state;  // Current state
  mcu_i2c_err_t result;
  uint16_t offset;  // Offset into the current seq buffer
  uint8_t bufIndx;  // Index into current seq buffer in use
  mcu_i2c_transfer_seq_t* seq;
} i2c_transfer_t;

static i2c_transfer_t i2cTransfer[I2C_COUNT];

// Lookup table for Nlow + Nhigh setting defined by CLHR. Set the undefined
// index (0x3) to reflect a default setting just in case.
static const uint8_t i2cNSum[] = {4 + 4, 6 + 3, 11 + 6, 4 + 4};

static bool PERIPHERALS_DATA bus_enabled[I2C_COUNT] = {false};

static mcu_i2c_err_t transfer_init(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq);
static mcu_i2c_err_t transfer_update(const mcu_i2c_device_t* device);
static void configure_gpios(const mcu_i2c_bus_config_t* config);
static void i2c_enable(mcu_i2c_bus_t* bus, const bool enable);
static void set_bus_freq(mcu_i2c_bus_t* bus, uint32_t freqRef, const uint32_t freqScl,
                         I2C_ClockHLR_TypeDef i2cMode);

void mcu_i2c_init(void) {
  _mcu_i2c_init();
}

void mcu_i2c_bus_init(const mcu_i2c_bus_config_t* config, const mcu_i2c_device_t* device,
                      const bool enable) {
  _mcu_i2c_bus_lock(config->peripheral);

  if (bus_enabled[config->peripheral]) {
    _mcu_i2c_bus_unlock(config->peripheral);
    return;
  }

  mcu_i2c_bus_t* bus = _mcu_i2c_handle(config->peripheral);
  ASSERT(I2C_REF_VALID(bus));

  _mcu_i2c_bus_init_state(config->peripheral);

  switch (config->peripheral) {
    case MCU_I2C0:
      CMU_ClockEnable(MCU_I2C0_CLOCK, true);
      break;
    case MCU_I2C1:
      CMU_ClockEnable(MCU_I2C1_CLOCK, true);
      break;
    default:
      break;
  }

  /* Configure GPIOs */
  configure_gpios(config);

  bus->IEN = 0;
  _mcu_i2c_int_clear(bus, _I2C_IF_MASK);

  /* Set SLAVE select mode */
  BUS_RegBitWrite(&(bus->CTRL), _I2C_CTRL_SLAVE_SHIFT, 0);

  /* Set bus frequency */
  mcu_i2c_set_bus_freq(device);

  i2c_enable(bus, enable);

  bus_enabled[config->peripheral] = true;

  _mcu_i2c_bus_unlock(config->peripheral);
}

void mcu_i2c_set_bus_freq(const mcu_i2c_device_t* device) {
  mcu_i2c_bus_t* bus = _mcu_i2c_handle(device->peripheral);

  switch (device->freq) {
    case MCU_I2C_FREQ_100K:
      set_bus_freq(bus, 0, I2C_FREQ_STANDARD_MAX, i2cClockHLRStandard);
      break;
    case MCU_I2C_FREQ_400K:
      set_bus_freq(bus, 0, I2C_FREQ_FAST_MAX, i2cClockHLRAsymetric);
      break;
    default:
      /* Unsupported frequency */
      ASSERT(false);
      break;
  }
}

static void configure_gpios(const mcu_i2c_bus_config_t* config) {
  mcu_gpio_set_mode(&config->scl, MCU_GPIO_MODE_OPEN_DRAIN_PULLUP_FILTER, true);
  mcu_gpio_set_mode(&config->sda, MCU_GPIO_MODE_OPEN_DRAIN_PULLUP_FILTER, true);

  const int id = config->peripheral;
  GPIO->I2CROUTE[id].ROUTEEN = GPIO_I2C_ROUTEEN_SDAPEN | GPIO_I2C_ROUTEEN_SCLPEN;
  GPIO->I2CROUTE[id].SDAROUTE = (uint32_t)((config->sda.pin << _GPIO_I2C_SDAROUTE_PIN_SHIFT) |
                                           (config->sda.port << _GPIO_I2C_SDAROUTE_PORT_SHIFT));
  GPIO->I2CROUTE[id].SCLROUTE = (uint32_t)((config->scl.pin << _GPIO_I2C_SCLROUTE_PIN_SHIFT) |
                                           (config->scl.port << _GPIO_I2C_SCLROUTE_PORT_SHIFT));
}

bool mcu_i2c_transfer_enter_critical(const mcu_i2c_device_t* device) {
  return _mcu_i2c_bus_critical_lock(device->peripheral);
}

bool mcu_i2c_transfer_exit_critical(const mcu_i2c_device_t* device) {
  return _mcu_i2c_bus_critical_unlock(device->peripheral);
}

mcu_i2c_err_t mcu_i2c_transfer(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                               const uint32_t timeout_ms) {
  if (!mcu_i2c_transfer_enter_critical(device)) {
    return MCU_I2C_TRANSFER_IN_PROGRESS;
  }

  const mcu_i2c_err_t ret = mcu_i2c_transfer_critical(device, seq, timeout_ms);

  if (!mcu_i2c_transfer_exit_critical(device)) {
    return MCU_I2C_TRANSFER_IN_PROGRESS;
  }

  return ret;
}

mcu_i2c_err_t mcu_i2c_transfer_critical(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                                        const uint32_t timeout_ms) {
  mcu_i2c_err_t result = transfer_init(device, seq);
  if (result < MCU_I2C_TRANSFER_DONE) {
    return result;
  }

  const uint32_t start = rtos_thread_systime();
  while (!RTOS_DEADLINE(start, timeout_ms)) {
    result = transfer_update(device);
    if (result != MCU_I2C_TRANSFER_IN_PROGRESS) {
      break;
    }
  }

  return result;
}

static mcu_i2c_err_t transfer_init(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq) {
  i2c_transfer_t* transfer;

  mcu_i2c_bus_t* bus = _mcu_i2c_handle(device->peripheral);

  ASSERT(I2C_REF_VALID(bus));
  ASSERT(seq);

  // Support up to 2 I2C buses
  if (bus == I2C0) {
    transfer = &i2cTransfer[0];
  }
#if (I2C_COUNT > 1)
  else if (bus == I2C1) {
    transfer = &i2cTransfer[1];
  }
#endif
#if (I2C_COUNT > 2)
  else if (bus == I2C2) {
    transfer = &i2cTransfer[2];
  }
#endif
  else {
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  _mcu_i2c_bus_init_lock(device->peripheral);

  transfer->state = MCU_I2C_STATE_START_ADDR_SEND;
  transfer->result = MCU_I2C_TRANSFER_IN_PROGRESS;
  transfer->offset = 0;
  transfer->bufIndx = 0;
  transfer->seq = seq;

  // Do not try to read 0 bytes. It is not
  // possible according to the I2C spec, since the slave will always start
  // sending the first byte ACK on an address. The read operation can
  // only be stopped by NACKing a received byte, i.e., minimum 1 byte.
  if (((seq->flags & MCU_I2C_FLAG_READ) && !(seq->buf[0].len)) ||
      ((seq->flags & MCU_I2C_FLAG_WRITE_READ) && !(seq->buf[1].len))) {
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  if (!_mcu_i2c_transfer_init(bus)) {
    _mcu_i2c_bus_init_unlock(device->peripheral);
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  mcu_i2c_err_t result = transfer_update(device);
  _mcu_i2c_bus_init_unlock(device->peripheral);

  return result;
}

// TODO: Pull states into separate functions to reduce size of this function.
static mcu_i2c_err_t transfer_update(const mcu_i2c_device_t* device) {
  uint32_t tmp;
  uint32_t pending;
  i2c_transfer_t* transfer;
  mcu_i2c_transfer_seq_t* seq;
  bool finished = false;

  mcu_i2c_bus_t* bus = _mcu_i2c_handle(device->peripheral);
  ASSERT(I2C_REF_VALID(bus));

  mcu_i2c_bus_state_t* _state = _mcu_i2c_bus_get_state(device->peripheral);
  perf_begin(_state->perf.transfers);

  /* Support up to 2 I2C buses. */
  if (bus == I2C0) {
    transfer = i2cTransfer;
  }
#if (I2C_COUNT > 1)
  else if (bus == I2C1) {
    transfer = i2cTransfer + 1;
  }
#endif
#if (I2C_COUNT > 2)
  else if (bus == I2C2) {
    transfer = i2cTransfer + 2;
  }
#endif
  else {
    perf_cancel(_state->perf.transfers);
    perf_count(_state->perf.errors);
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  seq = transfer->seq;

  _mcu_i2c_bus_lock(device->peripheral);
  while (!finished) {
    pending = bus->IF;

    /* If some sort of fault, abort transfer. */
    if (pending & I2C_IF_ERRORS) {
      if (pending & I2C_IF_ARBLOST) {
        // If an arbitration fault, indicates either a slave device
        // not responding as expected, or other master which is not
        // supported by this software.
        transfer->result = MCU_I2C_TRANSFER_ARB_LOST;
      } else if (pending & I2C_IF_BUSERR) {
        // A bus error indicates a misplaced start or stop, which should
        // not occur in master mode controlled by this software
        transfer->result = MCU_I2C_TRANSFER_BUS_ERR;
      }

      transfer->state = MCU_I2C_STATE_DONE;
      break;
    }

    switch (transfer->state) {
      // Send first start + address (first byte if 10 bit).
      case MCU_I2C_STATE_START_ADDR_SEND:
        if (seq->flags & MCU_I2C_FLAG_10BIT_ADDR) {
          tmp = (((uint32_t)(device->addr) >> 8) & 0x06) | 0xf0;

          /* In 10 bit address mode, the address following the first */
          /* start always indicates write. */
        } else {
          tmp = (uint32_t)(device->addr) & 0xfe;

          if (seq->flags & MCU_I2C_FLAG_READ) {
            /* Indicate read request */
            tmp |= 1;
          }
        }

        transfer->state = MCU_I2C_STATE_ADDR_WF_ACKNACK;
        bus->TXDATA = tmp; /* Data not transmitted until the START is sent. */
        bus->CMD = I2C_CMD_START;
        finished = true;
        break;

      /*******************************************************/
      /* Wait for ACK/NACK on the address (first byte if 10 bit). */
      /*******************************************************/
      case MCU_I2C_STATE_ADDR_WF_ACKNACK:
        if (pending & I2C_IF_NACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_NACK);
          transfer->result = MCU_I2C_TRANSFER_NACK;
          transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
          bus->CMD = I2C_CMD_STOP;
        } else if (pending & I2C_IF_ACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_ACK);

          /* If a 10 bit address, send the 2nd byte of the address. */
          if (seq->flags & MCU_I2C_FLAG_10BIT_ADDR) {
            transfer->state = MCU_I2C_STATE_ADDR_WF_2ND_ACKNACK;
            bus->TXDATA = (uint32_t)(device->addr) & 0xff;
          } else {
            /* Determine whether receiving or sending data. */
            if (seq->flags & MCU_I2C_FLAG_READ) {
              transfer->state = MCU_I2C_STATE_WF_DATA;
              if (seq->buf[transfer->bufIndx].len == 1) {
                bus->CMD = I2C_CMD_NACK;
              }
            } else {
              transfer->state = MCU_I2C_STATE_DATA_SEND;
              continue;
            }
          }
        }
        finished = true;
        break;

      /******************************************************/
      /* Wait for ACK/NACK on the second byte of a 10 bit address. */
      /******************************************************/
      case MCU_I2C_STATE_ADDR_WF_2ND_ACKNACK:
        if (pending & I2C_IF_NACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_NACK);
          transfer->result = MCU_I2C_TRANSFER_NACK;
          transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
          bus->CMD = I2C_CMD_STOP;
        } else if (pending & I2C_IF_ACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_ACK);

          /* If using a plain read sequence with a 10 bit address, switch to send */
          /* a repeated start. */
          if (seq->flags & MCU_I2C_FLAG_READ) {
            transfer->state = MCU_I2C_STATE_R_START_ADDR_SEND;
          }
          /* Otherwise, expected to write 0 or more bytes. */
          else {
            transfer->state = MCU_I2C_STATE_DATA_SEND;
          }
          continue;
        }
        finished = true;
        break;

      /*******************************/
      /* Send a repeated start+address */
      /*******************************/
      case MCU_I2C_STATE_R_START_ADDR_SEND:
        if (seq->flags & MCU_I2C_FLAG_10BIT_ADDR) {
          tmp = (uint32_t)((device->addr >> 8) & 0x06) | 0xf0;
        } else {
          tmp = (uint32_t)(device->addr & 0xfe);
        }

        /* If this is a write+read combined sequence, read is about to start. */
        if (seq->flags & MCU_I2C_FLAG_WRITE_READ) {
          /* Indicate a read request. */
          tmp |= 1;
          /* If reading only one byte, prepare the NACK now before START command. */
          if (seq->buf[transfer->bufIndx].len == 1) {
            bus->CMD = I2C_CMD_NACK;
          }
        }

        transfer->state = MCU_I2C_STATE_R_ADDR_WF_ACKNACK;
        /* The START command has to be written first since repeated start. Otherwise, */
        /* data would be sent first. */
        bus->CMD = I2C_CMD_START;
        bus->TXDATA = tmp;

        finished = true;
        break;

      /**********************************************************************/
      /* Wait for ACK/NACK on the repeated start+address (first byte if 10 bit) */
      /**********************************************************************/
      case MCU_I2C_STATE_R_ADDR_WF_ACKNACK:
        if (pending & I2C_IF_NACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_NACK);
          transfer->result = MCU_I2C_TRANSFER_NACK;
          transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
          bus->CMD = I2C_CMD_STOP;
        } else if (pending & I2C_IF_ACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_ACK);

          /* Determine whether receiving or sending data. */
          if (seq->flags & MCU_I2C_FLAG_WRITE_READ) {
            transfer->state = MCU_I2C_STATE_WF_DATA;
          } else {
            transfer->state = MCU_I2C_STATE_DATA_SEND;
            continue;
          }
        }
        finished = true;
        break;

      /*****************************/
      /* Send a data byte to the slave */
      /*****************************/
      case MCU_I2C_STATE_DATA_SEND:
        /* Reached end of data buffer. */
        if (transfer->offset >= seq->buf[transfer->bufIndx].len) {
          /* Move to the next message part. */
          transfer->offset = 0;
          transfer->bufIndx++;

          /* Send a repeated start when switching to read mode on the 2nd buffer. */
          if (seq->flags & MCU_I2C_FLAG_WRITE_READ) {
            transfer->state = MCU_I2C_STATE_R_START_ADDR_SEND;
            continue;
          }

          /* Only writing from one buffer or finished both buffers. */
          if ((seq->flags & MCU_I2C_FLAG_WRITE) || (transfer->bufIndx > 1)) {
            transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
            bus->CMD = I2C_CMD_STOP;
            finished = true;
            break;
          }

          /* Reprocess in case the next buffer is empty. */
          continue;
        }

        /* Send byte. */
        bus->TXDATA = (uint32_t)(seq->buf[transfer->bufIndx].data[transfer->offset++]);
        transfer->state = MCU_I2C_STATE_DATA_WF_ACKNACK;
        finished = true;
        break;

      /*********************************************************/
      /* Wait for ACK/NACK from the slave after sending data to it. */
      /*********************************************************/
      case MCU_I2C_STATE_DATA_WF_ACKNACK:
        if (pending & I2C_IF_NACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_NACK);
          transfer->result = MCU_I2C_TRANSFER_NACK;
          transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
          bus->CMD = I2C_CMD_STOP;
        } else if (pending & I2C_IF_ACK) {
          _mcu_i2c_int_clear(bus, I2C_IF_ACK);
          transfer->state = MCU_I2C_STATE_DATA_SEND;
          continue;
        }
        finished = true;
        break;

      /****************************/
      /* Wait for data from slave */
      /****************************/
      case MCU_I2C_STATE_WF_DATA:
        if (pending & I2C_IF_RXDATAV) {
          uint8_t data;
          unsigned int rx_len = seq->buf[transfer->bufIndx].len;

          /* Must read out data not to block further progress. */
          data = (uint8_t)(bus->RXDATA);

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

          /* SW needs to clear RXDATAV IF on Series 2 devices.
             Flag is kept high by HW if buffer is not empty. */
#if defined(_SILICON_LABS_32B_SERIES_2)
          _mcu_i2c_int_clear(bus, I2C_IF_RXDATAV);
#endif

          /* Make sure that there is no storing beyond the end of the buffer (just in case). */
          if (transfer->offset < rx_len) {
            seq->buf[transfer->bufIndx].data[transfer->offset++] = data;
          }

          /* If all requested data is read, the sequence should end. */
          if (transfer->offset >= rx_len) {
            transfer->state = MCU_I2C_STATE_WF_STOP_SENT;
            bus->CMD = I2C_CMD_STOP;
          } else {
            /* Send ACK and wait for the next byte. */
            bus->CMD = I2C_CMD_ACK;

            if ((1 < rx_len) && (transfer->offset == (rx_len - 1))) {
              /* If receiving more than one byte and this is the next
                 to last byte, transmit the NACK now before receiving
                 the last byte. */
              bus->CMD = I2C_CMD_NACK;
            }
          }
        }
        finished = true;
        break;

      /***********************************/
      /* Wait for STOP to have been sent */
      /***********************************/
      case MCU_I2C_STATE_WF_STOP_SENT:
        if (pending & I2C_IF_MSTOP) {
          _mcu_i2c_int_clear(bus, I2C_IF_MSTOP);
          transfer->state = MCU_I2C_STATE_DONE;
        }
        finished = true;
        break;

      /******************************/
      /* An unexpected state, software fault */
      /******************************/
      default:
        transfer->result = MCU_I2C_TRANSFER_SW_FAULT;
        transfer->state = MCU_I2C_STATE_DONE;
        finished = true;
        break;
    }
  }

  if (transfer->state == MCU_I2C_STATE_DONE) {
    /* Disable interrupt sources when done. */
    bus->IEN = 0;

    /* Update the result unless a fault has already occurred. */
    if (transfer->result == MCU_I2C_TRANSFER_IN_PROGRESS) {
      transfer->result = MCU_I2C_TRANSFER_DONE;
    }
  }
  /* Until transfer is done, keep returning i2cTransferInProgress. */
  else {
    _mcu_i2c_bus_unlock(device->peripheral);
    return MCU_I2C_TRANSFER_IN_PROGRESS;
  }

  if (transfer->result < MCU_I2C_TRANSFER_DONE) {
    perf_count(_state->perf.errors);
  }
  if (transfer->result == MCU_I2C_TRANSFER_DONE) {
    perf_end(_state->perf.transfers);
  }

  _mcu_i2c_bus_unlock(device->peripheral);
  return transfer->result;
}

static void i2c_enable(mcu_i2c_bus_t* bus, const bool enable) {
  ASSERT(I2C_REF_VALID(bus));

#if defined(_I2C_EN_MASK)
  BUS_RegBitWrite(&(bus->EN), _I2C_EN_EN_SHIFT, enable);
#else
  BUS_RegBitWrite(&(bus->CTRL), _I2C_CTRL_EN_SHIFT, enable);
#endif
}

static void set_bus_freq(mcu_i2c_bus_t* bus, uint32_t freqRef, const uint32_t freqScl,
                         I2C_ClockHLR_TypeDef i2cMode) {
  uint32_t n, minFreq, denominator;
  int32_t div;

  /* Avoid dividing by 0. */
  ASSERT(freqScl);
  if (!freqScl) {
    return;
  }

  /* Ensure mode is valid */
  i2cMode &= _I2C_CTRL_CLHR_MASK >> _I2C_CTRL_CLHR_SHIFT;

  /* Set the CLHR (clock low-to-high ratio). */
  bus->CTRL &= ~_I2C_CTRL_CLHR_MASK;
  BUS_RegMaskedWrite(&bus->CTRL, _I2C_CTRL_CLHR_MASK, i2cMode << _I2C_CTRL_CLHR_SHIFT);

  if (freqRef == 0) {
    if (bus == I2C0) {
      freqRef = CMU_ClockFreqGet(cmuClock_I2C0);
#if defined(I2C1)
    } else if (bus == I2C1) {
      freqRef = CMU_ClockFreqGet(cmuClock_I2C1);
#endif
#if defined(I2C2)
    } else if (bus == I2C2) {
      freqRef = CMU_ClockFreqGet(cmuClock_I2C2);
#endif
    } else {
      ASSERT(false);
    }
  }

  /* Check the minimum HF peripheral clock. */
  minFreq = UINT32_MAX;
  if (bus->CTRL & I2C_CTRL_SLAVE) {
    switch (i2cMode) {
      case i2cClockHLRStandard:
#if defined(_SILICON_LABS_32B_SERIES_0)
        minFreq = 4200000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_1)
        minFreq = 2000000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_2)
        minFreq = 2000000;
        break;
#endif
      case i2cClockHLRAsymetric:
#if defined(_SILICON_LABS_32B_SERIES_0)
        minFreq = 11000000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_1)
        minFreq = 5000000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_2)
        minFreq = 5000000;
        break;
#endif
      case i2cClockHLRFast:
#if defined(_SILICON_LABS_32B_SERIES_0)
        minFreq = 24400000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_1)
        minFreq = 14000000;
        break;
#elif defined(_SILICON_LABS_32B_SERIES_2)
        minFreq = 14000000;
        break;
#endif
      default:
        /* MISRA requires the default case. */
        break;
    }
  } else {
    /* For master mode, platform 1 and 2 share the same
       minimum frequencies. */
    switch (i2cMode) {
      case i2cClockHLRStandard:
        minFreq = 2000000;
        break;
      case i2cClockHLRAsymetric:
        minFreq = 9000000;
        break;
      case i2cClockHLRFast:
        minFreq = 20000000;
        break;
      default:
        /* MISRA requires default case */
        break;
    }
  }

  /* Frequency most be larger-than. */
  ASSERT(freqRef > minFreq);

  /* SCL frequency is given by:
   * freqScl = freqRef/((Nlow + Nhigh) * (DIV + 1) + I2C_CR_MAX)
   *
   * Therefore,
   * DIV = ((freqRef - (I2C_CR_MAX * freqScl))/((Nlow + Nhigh) * freqScl)) - 1
   *
   * For more details, see the reference manual
   * I2C Clock Generation chapter.  */

  /* n = Nlow + Nhigh */
  n = (uint32_t)i2cNSum[i2cMode];
  denominator = n * freqScl;

  /* Explicitly ensure denominator is never zero. */
  if (denominator == 0) {
    ASSERT(0);
    return;
  }
  /* Perform integer division so that div is rounded up. */
  div = (int32_t)(((freqRef - (I2C_CR_MAX * freqScl) + denominator - 1) / denominator) - 1);
  ASSERT(div >= 0);
  ASSERT((uint32_t)div <= _I2C_CLKDIV_DIV_MASK);

  /* The clock divisor must be at least 1 in slave mode according to the reference */
  /* manual (in which case there is normally no need to set the bus frequency). */
  if ((bus->CTRL & I2C_CTRL_SLAVE) && (div == 0)) {
    div = 1;
  }
  bus->CLKDIV = (uint32_t)div;
}
