#include "mcu_i2c_priv.h"

static const uint32_t flush_rx_timeout_ms = 5;

static mcu_i2c_bus_state_t PERIPHERALS_DATA state[I2C_COUNT] = {0};

static void flush_rx(mcu_i2c_bus_t* bus);

void _mcu_i2c_init(void) {
  for (mcu_i2c_t i = 0; i < I2C_COUNT; i++) {
    rtos_mutex_create(&state[i].init_access);
    rtos_mutex_create(&state[i].transfer_access);
    rtos_mutex_create(&state[i].ctitical_access);
  }
}

mcu_i2c_bus_t* _mcu_i2c_handle(const mcu_i2c_t peripheral) {
  switch (peripheral) {
    case MCU_I2C0:
      return I2C0;
    case MCU_I2C1:
      return I2C1;
    default:
      ASSERT(false);  // Should be unreachable.
      break;
  }
}

void _mcu_i2c_int_clear(mcu_i2c_bus_t* bus, const uint32_t flags) {
#if defined(I2C_HAS_SET_CLEAR)
  bus->IF_CLR = flags;
#else
  bus->IFC = flags;
#endif
}

bool _mcu_i2c_transfer_init(mcu_i2c_bus_t* bus) {
  // Check if in a busy state. Since this software assumes a single master,
  // issue an abort. The BUSY state is normal after a reset.
  if (bus->STATE & I2C_STATE_BUSY) {
    bus->CMD = I2C_CMD_ABORT;
  }

  // Ensure buffers are empty.
  bus->CMD = I2C_CMD_CLEARPC | I2C_CMD_CLEARTX;
  flush_rx(bus);

  // Clear all pending interrupts prior to starting a transfer.
  _mcu_i2c_int_clear(bus, _I2C_IF_MASK);

  // Enable relevant interrupts.
  // Notice that the I2C interrupt must also be enabled in the NVIC, but
  // that is left for an additional driver wrapper.
  bus->IEN |= I2C_IEN_NACK | I2C_IEN_ACK | I2C_IEN_MSTOP | I2C_IEN_RXDATAV | I2C_IEN_ERRORS;

  return true;
}

mcu_i2c_bus_state_t* _mcu_i2c_bus_get_state(const mcu_i2c_t peripheral) {
  return &state[peripheral];
}

void _mcu_i2c_bus_init_state(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);

  switch (peripheral) {
    case MCU_I2C0:
      state->perf.transfers = perf_create(PERF_ELAPSED, i2c0_transfers);
      state->perf.errors = perf_create(PERF_COUNT, i2c0_errors);
      break;
    case MCU_I2C1:
      state->perf.transfers = perf_create(PERF_ELAPSED, i2c1_transfers);
      state->perf.errors = perf_create(PERF_COUNT, i2c1_errors);
      break;
    default:
      break;
  }
}

bool _mcu_i2c_bus_lock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_lock(&state[peripheral].transfer_access);
}

bool _mcu_i2c_bus_unlock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_unlock(&state[peripheral].transfer_access);
}

bool _mcu_i2c_bus_init_lock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_lock(&state[peripheral].init_access);
}

bool _mcu_i2c_bus_init_unlock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_unlock(&state[peripheral].init_access);
}

bool _mcu_i2c_bus_ctitical_lock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_lock(&state[peripheral].ctitical_access);
}

bool _mcu_i2c_bus_critical_unlock(const mcu_i2c_t peripheral) {
  ASSERT(peripheral < I2C_COUNT);
  return rtos_mutex_unlock(&state[peripheral].ctitical_access);
}

static void flush_rx(mcu_i2c_bus_t* bus) {
  const uint32_t start = rtos_thread_systime();
  while (bus->STATUS & I2C_STATUS_RXDATAV && !RTOS_DEADLINE(start, flush_rx_timeout_ms)) {
    bus->RXDATA;
  }

#if defined(_SILICON_LABS_32B_SERIES_2)
  // SW needs to clear RXDATAV IF on Series 2 devices.
  // Flag is kept high by HW if buffer is not empty.
  _mcu_i2c_int_clear(bus, I2C_IF_RXDATAV);
#endif
}
