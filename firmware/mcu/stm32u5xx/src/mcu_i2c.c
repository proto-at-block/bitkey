#include "mcu_i2c.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "mcu_dma.h"
#include "mcu_dma_impl.h"
#include "mcu_gpio.h"
#include "mcu_nvic.h"
#include "mcu_nvic_impl.h"
#include "perf.h"
#include "platform.h"
#include "rtos.h"
#include "stm32u5xx_ll_bus.h"
#include "stm32u5xx_ll_dma.h"
#include "stm32u5xx_ll_i2c.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/**
 * @brief Timing value for 100kHz at 160MHz PCLK.
 *
 * @note These are calculated using STM32CubeMX timing calculator:
 * PCLK1 = 160MHz, 100kHz: PRESC=15, SCLL=0x31, SCLH=0x31, SDADEL=0x0, SCLDEL=0x4
 */
#define MCU_I2C_TIMING_100KHZ 0xF0403131U

/**
 * @brief Timing value for 400kHz at 160MHz PCLK.
 *
 * @note These are calculated using STM32CubeMX timing calculator:
 * PCLK1 = 160MHz, 400kHz: PRESC=3, SCLL=0x37, SCLH=0x1C, SDADEL=0x1, SCLDEL=0x3
 */
#define MCU_I2C_TIMING_400KHZ 0x30311C37U

/**
 * @brief Maximum transfer size as defined in the reference manual.
 */
#define MCU_I2C_MAX_XFER_SIZE 0xFFu

typedef struct {
  /**
   * @brief Active transfer sequence.
   */
  const mcu_i2c_transfer_seq_t* seq;

  /**
   * @brief Current buffer index within the #mcu_i2c_transfer_t::seq.
   */
  uint8_t buf_index;

  /**
   * @brief Offset into current buffer within the #mcu_i2c_transfer_t::seq.
   */
  uint32_t offset;

  /**
   * @brief Remaining bytes across the entire transfer (inclusive of all
   * buffers).
   */
  uint32_t remaining;
} mcu_i2c_transfer_t;

typedef struct {
  /**
   * @brief Instance identifier.
   */
  mcu_i2c_t instance;

  /**
   * @brief Base for the I2C registers.
   */
  void* i2c_base;

#if IMAGE_TYPE_APPLICATION
  /**
   * @brief Provides exclusive access to the I2C instance.
   */
  rtos_mutex_t access;

  /**
   * @brief Transfer event group.
   */
  rtos_event_group_t events;
#else
  /**
   * @brief Transfer event bitmask.
   */
  volatile uint32_t events;
#endif

  /**
   * @brief DMA channels.
   */
  struct {
    uint32_t rx;  //<! RX channel for DMA.
    uint32_t tx;  //<! TX channel for DMA.
  } dma;

  /**
   * @brief Transfer state.
   */
  mcu_i2c_transfer_t transfer;

  /**
   * @brief Performance counters.
   */
  struct {
    perf_counter_t* transfers;  //<! Number of successful transfers.
    perf_counter_t* errors;     //<! Number of errors.
  } perf;

  /**
   * @brief True if instance already initialized.
   */
  bool initialized;
} mcu_i2c_bus_state_t;

/**
 * @brief Transfer event bit mask.
 */
typedef enum {
  MCU_I2C_EVENT_NONE = 0x000,           //<! None (unused).
  MCU_I2C_EVENT_TRANSFER_DONE = 0x001,  //<! Transfer complete.
  MCU_I2C_EVENT_BUS_ERROR = 0x002,      //<! Bus error.
  MCU_I2C_EVENT_NACK = 0x004,           //<! NACK
} mcu_i2c_transfer_event_t;

static mcu_i2c_bus_state_t PERIPHERALS_DATA _state[PLATFORM_CFG_MCU_I2C_CNT] = {0};

/**
 * @brief Allocates an instance of an I2C state object.
 *
 * @param instance  I2C instance identifier.
 *
 * @return Pointer to an un-used instance, otherwise `NULL` on failure.
 */
static mcu_i2c_bus_state_t* _mcu_i2c_alloc_state(mcu_i2c_t instance);

/**
 * @brief Retrieves a pointer to an I2C state object.
 *
 * @param instance  I2C instance identifier.
 *
 * @return Pointer to an allocated instance, otherwise `NULL` if no instance
 * matches the given identifier, @p instance.
 */
static mcu_i2c_bus_state_t* _mcu_i2c_get_state(mcu_i2c_t instance);

/**
 * @brief Locks access to the I2C instance.
 *
 * @param state  I2C instance state.
 */
static void _mcu_i2c_lock(mcu_i2c_bus_state_t* state);

/**
 * @brief Unlocks access to the I2C instance.
 *
 * @param state  I2C instance state.
 */
static void _mcu_i2c_unlock(mcu_i2c_bus_state_t* state);

/**
 * @brief Configures the I2C frequency for an I2C instance.
 *
 * @param i2c   Pointer to the I2C registers.
 * @param freq  Desired frequency.
 */
static void _mcu_i2c_set_bus_freq(I2C_TypeDef* i2c, mcu_i2c_freq_t freq);

/**
 * @brief I2C TX/RX/NACK/STOP interrupt handler.
 *
 * @param i2c_base  Base address of the I2C registers.
 */
static void _mcu_i2c_int_handler(void* i2c_base);

/**
 * @brief I2C error interrupt handler.
 *
 * @param i2c_base  Base address of the I2C registers.
 */
static void _mcu_i2c_err_handler(void* i2c_base);

/**
 * @brief DMA callback for I2C transfers.
 *
 * @details This callback is invoked when a DMA transfer completes. It updates
 * the transfer state and configures the next DMA transfer if needed.
 *
 * @param channel     DMA channel number (unused).
 * @param flag        DMA transfer complete flags.
 * @param user_param  I2C state instance.
 *
 * @return `true`
 */
static bool _mcu_i2c_dma_callback(uint32_t channel, uint32_t flags, void* user_param);

/**
 * @brief Starts a DMA transfer for the current I2C operation.
 *
 * @details Configures and starts DMA for either TX or RX based on the current
 * transfer state.
 *
 * @param state    Pointer to the I2C instance state.
 * @param transfer Current I2C transfer.
 */
static mcu_err_t _mcu_i2c_start_dma_transfer(mcu_i2c_bus_state_t* state,
                                             mcu_i2c_transfer_t* transfer);

/**
 * @brief Configures the next transfer chunk when TCR interrupt fires.
 *
 * @details This function is called from the interrupt handler when the
 * Transfer Complete Reload (TCR) flag is set, indicating that the current
 * chunk is done and the I2C peripheral is ready for the next chunk
 * configuration. The I2C control registers allows a maximum transfer size
 * of 255 bytes. If the caller wishes to transmit or receive more than 255
 * bytes at a given time, they must use the reload functionality to provide
 * the next chunk of bytes or set up the next address to receive data into.
 *
 * @param state    Pointer to the I2C instance state.
 * @param transfer Current I2C transfer.
 * @param i2c      Pointer to the I2C registers.
 *
 * @return #MCU_ERROR_OK on success, otherwise an error code.
 */
static mcu_err_t _mcu_i2c_configure_next_transfer(mcu_i2c_bus_state_t* state,
                                                  mcu_i2c_transfer_t* transfer, I2C_TypeDef* i2c);

/**
 * @brief Disables the I2C interrupts.
 *
 * @param i2c  Pointer to the I2C registers.
 */
static void _mcu_i2c_disable_interrupts(I2C_TypeDef* i2c);

/**
 * @brief Signals an I2C event to the pending thread.
 *
 * @param state  Pointer to the I2C state instance.
 * @param event  The I2C event to signal.
 */
static void _mcu_i2c_post_event(mcu_i2c_bus_state_t* state, mcu_i2c_transfer_event_t event);

/**
 * @brief Waits for an I2C event to happen.
 *
 * @param state       Pointer to the I2C state instance.
 * @param events      Event bitmask.
 * @param timeout_ms  Timeout (in milliseconds) to wait for an event.
 *
 * @return Bitmask of events that were signaled.
 */
static uint32_t _mcu_i2c_pend_events(mcu_i2c_bus_state_t* state, uint32_t events,
                                     uint32_t timeout_ms);

void mcu_i2c_init(void) {
#if IMAGE_TYPE_APPLICATION
  for (size_t i = 0; i < ARRAY_SIZE(_state); i++) {
    rtos_mutex_create(&_state[i].access);
    rtos_event_group_create(&_state[i].events);
  }
#endif
}

void mcu_i2c_bus_init(const mcu_i2c_bus_config_t* config, const mcu_i2c_device_t* device,
                      const bool enable) {
  ASSERT(config != NULL);
  ASSERT(device != NULL);

  mcu_i2c_bus_state_t* state = _mcu_i2c_get_state(config->peripheral);
  if (state == NULL) {
    state = _mcu_i2c_alloc_state(config->peripheral);
    ASSERT(state != NULL);
  }

  if (state->initialized) {
    return;
  }

  state->initialized = true;
  _mcu_i2c_lock(state);

  /* Find instance and enable clock. */
  switch (state->instance) {
    case MCU_I2C1:
      state->i2c_base = (void*)I2C1;
      state->perf.transfers = perf_create(PERF_ELAPSED, i2c1_transfers);
      state->perf.errors = perf_create(PERF_COUNT, i2c1_errors);
      LL_APB1_GRP1_EnableClock(LL_APB1_GRP1_PERIPH_I2C1);
      break;

    case MCU_I2C0:
      /* 'break' intentionally omitted. */

    default:
      ASSERT(false);
  }

  ASSERT(state->i2c_base != NULL);

  mcu_err_t err = mcu_dma_allocate_channel(&state->dma.tx);
  ASSERT(err == MCU_ERROR_OK);

  err = mcu_dma_allocate_channel(&state->dma.rx);
  ASSERT(err == MCU_ERROR_OK);

  /* Configure the GPIOs. */
  mcu_gpio_configure(&config->sda, false);
  mcu_gpio_configure(&config->scl, false);

  /* Ensure SDA/SCL are configured for open-drain as required by the I2C spec. */
  config->sda.port->OTYPER |= (1UL << config->sda.pin);
  config->scl.port->OTYPER |= (1UL << config->scl.pin);

  /* Release the bus by default (open-drain outputs idle high). */
  config->sda.port->BSRR = (1UL << config->sda.pin);
  config->scl.port->BSRR = (1UL << config->scl.pin);

  /* Disable I2C before configuration. */
  I2C_TypeDef* i2c = (I2C_TypeDef*)state->i2c_base;
  LL_I2C_Disable(i2c);

  /* Configure I2C in master mode. */
  LL_I2C_SetMode(i2c, LL_I2C_MODE_I2C);
  LL_I2C_EnableAnalogFilter(i2c);
  LL_I2C_SetDigitalFilter(i2c, 0);

  /* Disable own address. */
  LL_I2C_DisableOwnAddress1(i2c);
  LL_I2C_DisableOwnAddress2(i2c);

  /* Set bus frequency. */
  _mcu_i2c_set_bus_freq(i2c, device->freq);

  /* Enable I2C interrupts. */
  switch (state->instance) {
    case MCU_I2C1:
      mcu_nvic_set_priority(I2C1_EV_IRQn, MCU_NVIC_DEFAULT_IRQ_PRIORITY);
      mcu_nvic_enable_irq(I2C1_EV_IRQn);
      mcu_nvic_set_priority(I2C1_ER_IRQn, MCU_NVIC_DEFAULT_IRQ_PRIORITY);
      mcu_nvic_enable_irq(I2C1_ER_IRQn);
      break;

    case MCU_I2C0:
      /* 'break' intentionally omitted. */

    default:
      ASSERT(false);
  }

  /* Enable I2C if requested. */
  if (enable) {
    LL_I2C_Enable(i2c);
  }

  _mcu_i2c_unlock(state);
}

void mcu_i2c_set_bus_freq(const mcu_i2c_device_t* device) {
  ASSERT(device != NULL);

  mcu_i2c_bus_state_t* state = _mcu_i2c_get_state(device->peripheral);
  ASSERT(state != NULL);

  I2C_TypeDef* i2c = (I2C_TypeDef*)state->i2c_base;

  /* Disable I2C before changing timing. */
  const bool was_enabled = LL_I2C_IsEnabled(i2c);
  if (was_enabled) {
    LL_I2C_Disable(i2c);
  }

  _mcu_i2c_set_bus_freq(i2c, device->freq);

  if (was_enabled) {
    LL_I2C_Enable(i2c);
  }
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

bool mcu_i2c_transfer_enter_critical(const mcu_i2c_device_t* device) {
  ASSERT(device != NULL);

#if IMAGE_TYPE_APPLICATION
  mcu_i2c_bus_state_t* state = _mcu_i2c_get_state(device->peripheral);
  ASSERT(state != NULL);
  return rtos_mutex_lock(&state->access);
#else
  return true;
#endif
}

bool mcu_i2c_transfer_exit_critical(const mcu_i2c_device_t* device) {
  ASSERT(device != NULL);

#if IMAGE_TYPE_APPLICATION
  mcu_i2c_bus_state_t* state = _mcu_i2c_get_state(device->peripheral);
  ASSERT(state != NULL);
  return rtos_mutex_unlock(&state->access);
#else
  return true;
#endif
}

mcu_i2c_err_t mcu_i2c_transfer_critical(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                                        const uint32_t timeout_ms) {
  ASSERT(device != NULL);
  ASSERT(seq != NULL);

  if ((seq->flags & MCU_I2C_FLAG_WRITE_READ) == MCU_I2C_FLAG_WRITE_READ) {
    /* WRITE-READ must be split into two separate transfers. */
    mcu_i2c_transfer_seq_t write_seq = {
      .flags = MCU_I2C_FLAG_WRITE,
      .buf[0] = seq->buf[0],
      .buf[1] =
        {
          .data = NULL,
          .len = 0,
        },
    };

    const mcu_i2c_err_t err = mcu_i2c_transfer_critical(device, &write_seq, timeout_ms);
    if (err != MCU_I2C_TRANSFER_DONE) {
      return err;
    }

    mcu_i2c_transfer_seq_t read_seq = {
      .flags = MCU_I2C_FLAG_READ,
      .buf[0] = seq->buf[1],
      .buf[1] =
        {
          .data = NULL,
          .len = 0,
        },
    };

    return mcu_i2c_transfer_critical(device, &read_seq, timeout_ms);
  }

  mcu_i2c_bus_state_t* state = _mcu_i2c_get_state(device->peripheral);
  ASSERT(state != NULL);

  I2C_TypeDef* i2c = (I2C_TypeDef*)state->i2c_base;
  const uint32_t flags = seq->flags;

  if (!seq->buf[0].len || !seq->buf[0].data) {
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  /* Initialize transfer state. */
  mcu_i2c_transfer_t* transfer = &state->transfer;
  transfer->seq = seq;
  transfer->buf_index = 0;
  transfer->offset = 0;
  transfer->remaining = 0;

  /* Compute total number of bytes to transfer. */
  for (size_t i = 0; i < ARRAY_SIZE(seq->buf); i++) {
    transfer->remaining += seq->buf[i].len;
  }

  /* Set master addressing mode. */
  LL_I2C_SetMasterAddressingMode(
    i2c,
    (flags & MCU_I2C_FLAG_10BIT_ADDR ? LL_I2C_ADDRESSING_MODE_10BIT : LL_I2C_ADDRESSING_MODE_7BIT));

  /* Set slave address. For 7-bit addressing, the address must be left-shifted by 1 bit */
  /* as the STM32 I2C peripheral expects the 7-bit address in bits [7:1] of the SADD field. */
  const uint32_t slave_addr =
    (flags & MCU_I2C_FLAG_10BIT_ADDR) ? device->addr : (device->addr << 1);
  LL_I2C_SetSlaveAddr(i2c, slave_addr);

  /* Determine and set initial transfer size. */
  const uint32_t initial_size = BLK_MIN(seq->buf[0].len, MCU_I2C_MAX_XFER_SIZE);
  LL_I2C_SetTransferSize(i2c, initial_size);

  /* Set transfer direction for the first buffer. */
  if (flags & MCU_I2C_FLAG_READ) {
    LL_I2C_SetTransferRequest(i2c, LL_I2C_REQUEST_READ);
  } else {
    LL_I2C_SetTransferRequest(i2c, LL_I2C_REQUEST_WRITE);
  }

  /* Enable reload mode if we need more than one DMA transfer. */
  if (transfer->remaining > initial_size) {
    LL_I2C_EnableReloadMode(i2c);
  } else {
    LL_I2C_DisableReloadMode(i2c);
  }

  /* Enable error interrupts. */
  LL_I2C_EnableIT_NACK(i2c);
  LL_I2C_EnableIT_ERR(i2c);

  /* Configure and start DMA transfer. */
  const mcu_err_t err = _mcu_i2c_start_dma_transfer(state, transfer);
  if (err != MCU_ERROR_OK) {
    return MCU_I2C_TRANSFER_USAGE_FAULT;
  }

  /* Start the I2C transfer. */
  perf_begin(state->perf.transfers);
  LL_I2C_GenerateStartCondition(i2c);

  const uint32_t bits = _mcu_i2c_pend_events(
    state, (MCU_I2C_EVENT_TRANSFER_DONE | MCU_I2C_EVENT_BUS_ERROR | MCU_I2C_EVENT_NACK),
    timeout_ms);

  if (bits != MCU_I2C_EVENT_TRANSFER_DONE) {
    /* Stop DMA transfers. */
    (void)mcu_dma_channel_stop(state->dma.rx);
    (void)mcu_dma_channel_stop(state->dma.tx);

    /* Disable I2C DMA requests. */
    LL_I2C_DisableDMAReq_TX(i2c);
    LL_I2C_DisableDMAReq_RX(i2c);

    /* Abort the transfer by disabling the peripheral. */
    LL_I2C_Disable(i2c);
    while (LL_I2C_IsEnabled(i2c)) {
      ;
    }

    /* Timing must be set again on failure. */
    mcu_i2c_set_bus_freq(device);

    /* Re-enable the I2C instance. */
    LL_I2C_Enable(i2c);

    perf_cancel(state->perf.transfers);
    perf_count(state->perf.errors);

    if (bits & MCU_I2C_EVENT_NACK) {
      return MCU_I2C_TRANSFER_NACK;
    } else if (bits & MCU_I2C_EVENT_BUS_ERROR) {
      return MCU_I2C_TRANSFER_BUS_ERR;
    }

    ASSERT(bits == 0);
    return MCU_I2C_TRANSFER_TIMEOUT;
  }

  /* Transfer completed successfully. */
  perf_end(state->perf.transfers);

  return MCU_I2C_TRANSFER_DONE;
}

static mcu_i2c_bus_state_t* _mcu_i2c_alloc_state(mcu_i2c_t instance) {
  for (size_t i = 0; i < ARRAY_SIZE(_state); i++) {
    if (!_state[i].initialized) {
      _state[i].instance = instance;
      return &_state[i];
    }
  }
  return NULL;
}

static mcu_i2c_bus_state_t* _mcu_i2c_get_state(mcu_i2c_t instance) {
  for (size_t i = 0; i < ARRAY_SIZE(_state); i++) {
    if (_state[i].initialized && (_state[i].instance == instance)) {
      return &_state[i];
    }
  }
  return NULL;
}

static void _mcu_i2c_lock(mcu_i2c_bus_state_t* state) {
  ASSERT(state != NULL);
#if IMAGE_TYPE_APPLICATION
  ASSERT(rtos_mutex_lock(&state->access));
#endif
}

static void _mcu_i2c_unlock(mcu_i2c_bus_state_t* state) {
  ASSERT(state != NULL);
#if IMAGE_TYPE_APPLICATION
  ASSERT(rtos_mutex_unlock(&state->access));
#endif
}

static void _mcu_i2c_set_bus_freq(I2C_TypeDef* i2c, mcu_i2c_freq_t freq) {
  ASSERT(i2c != NULL);
  switch (freq) {
    case MCU_I2C_FREQ_100K:
      LL_I2C_SetTiming(i2c, MCU_I2C_TIMING_100KHZ);
      break;

    case MCU_I2C_FREQ_400K:
      /* 'break' intentionally omitted. */

    case MCU_I2C_FREQ_MAX:
      LL_I2C_SetTiming(i2c, MCU_I2C_TIMING_400KHZ);
      break;

    default:
      ASSERT(false);
  }
}

static void _mcu_i2c_int_handler(void* i2c_base) {
  ASSERT(i2c_base != NULL);

  mcu_i2c_bus_state_t* state = NULL;

  for (size_t i = 0; i < ARRAY_SIZE(_state); i++) {
    if (_state[i].initialized && (_state[i].i2c_base == i2c_base)) {
      state = &_state[i];
      break;
    }
  }

  if (state == NULL) {
    return;
  }

  I2C_TypeDef* i2c = (I2C_TypeDef*)i2c_base;
  mcu_i2c_transfer_t* transfer = &state->transfer;

  if (LL_I2C_IsActiveFlag_TCR(i2c) || LL_I2C_IsActiveFlag_TC(i2c)) {
    /* Disable TC IRQ before configuring next transfer. */
    LL_I2C_DisableIT_TC(i2c);

    /* Configure next chunk to transfer. */
    const mcu_err_t err = _mcu_i2c_configure_next_transfer(state, transfer, i2c);

    /* Transfer is complete or we failed to configure the next transfer. */
    if ((err != MCU_ERROR_OK) || (transfer->remaining == 0)) {
      _mcu_i2c_disable_interrupts(i2c);

      /* Stop DMA transfers. */
      (void)mcu_dma_channel_stop(state->dma.rx);
      (void)mcu_dma_channel_stop(state->dma.tx);

      /* Disable I2C DMA requests. */
      LL_I2C_DisableDMAReq_TX(i2c);
      LL_I2C_DisableDMAReq_RX(i2c);

      /* Generate stop condition. */
      LL_I2C_GenerateStopCondition(i2c);

      /* Signal transfer complete. */
      _mcu_i2c_post_event(
        state, (err == MCU_ERROR_OK ? MCU_I2C_EVENT_TRANSFER_DONE : MCU_I2C_EVENT_BUS_ERROR));
    }
  }

  /* Handle NACK. */
  if (LL_I2C_IsActiveFlag_NACK(i2c)) {
    LL_I2C_ClearFlag_NACK(i2c);
    LL_I2C_ClearFlag_ADDR(i2c);

    _mcu_i2c_disable_interrupts(i2c);

    /* Stop DMA transfers. */
    (void)mcu_dma_channel_stop(state->dma.rx);
    (void)mcu_dma_channel_stop(state->dma.tx);

    /* Signal NACK. */
    _mcu_i2c_post_event(state, MCU_I2C_EVENT_NACK);
  }
}

static void _mcu_i2c_err_handler(void* i2c_base) {
  ASSERT(i2c_base != NULL);

  I2C_TypeDef* i2c = i2c_base;
  mcu_i2c_transfer_event_t event = MCU_I2C_EVENT_NONE;

  if (LL_I2C_IsActiveFlag_BERR(i2c)) {
    LL_I2C_ClearFlag_BERR(i2c);
    event = MCU_I2C_EVENT_BUS_ERROR;
  }

  if (LL_I2C_IsActiveFlag_ARLO(i2c)) {
    LL_I2C_ClearFlag_ARLO(i2c);
  }

  if (LL_I2C_IsActiveFlag_OVR(i2c)) {
    LL_I2C_ClearFlag_OVR(i2c);
  }

  if (event == MCU_I2C_EVENT_NONE) {
    return;
  }

  /* Disable interrupts. */
  LL_I2C_DisableIT_TC(i2c);
  LL_I2C_DisableIT_NACK(i2c);
  LL_I2C_DisableIT_ERR(i2c);

  for (size_t i = 0; i < ARRAY_SIZE(_state); i++) {
    if (_state[i].initialized && (_state[i].i2c_base == i2c_base)) {
      _mcu_i2c_post_event(&_state[i], event);
      break;
    }
  }
}

static bool _mcu_i2c_dma_callback(uint32_t channel, uint32_t flags, void* user_param) {
  (void)channel;

  mcu_i2c_bus_state_t* state = (mcu_i2c_bus_state_t*)user_param;
  ASSERT(state != NULL);

  if (flags & MCU_DMA_FLAG_TRANSFER_COMPLETE) {
    /* Enable transfer complete interrupts. */
    LL_I2C_EnableIT_TC((I2C_TypeDef*)state->i2c_base);
  } else if (flags & MCU_DMA_FLAG_TRANSFER_ERROR) {
    /* DMA error - signal bus error. */
    _mcu_i2c_post_event(state, MCU_I2C_EVENT_BUS_ERROR);
  }

  return true;
}

static mcu_err_t _mcu_i2c_start_dma_transfer(mcu_i2c_bus_state_t* state,
                                             mcu_i2c_transfer_t* transfer) {
  ASSERT(state != NULL);
  ASSERT(transfer != NULL);

  I2C_TypeDef* i2c = (I2C_TypeDef*)state->i2c_base;
  const mcu_i2c_transfer_seq_t* seq = transfer->seq;

  /* Determine transfer size (remaining in current buffer, capped at max). */
  const uint32_t current_buf_len = seq->buf[transfer->buf_index].len;
  ASSERT(current_buf_len >= transfer->offset);
  const uint32_t remaining_in_buf = current_buf_len - transfer->offset;
  const uint32_t xfer_size = BLK_MIN(remaining_in_buf, MCU_I2C_MAX_XFER_SIZE);

  /* Determine DMA request based on peripheral instance. */
  uint32_t dma_request_tx = 0;
  uint32_t dma_request_rx = 0;

  switch (state->instance) {
    case MCU_I2C1:
      dma_request_tx = MCU_DMA_SIGNAL_I2C1_TX;
      dma_request_rx = MCU_DMA_SIGNAL_I2C1_RX;
      break;

    case MCU_I2C0:
      /* 'break' intentionally omitted. */

    default:
      return MCU_ERROR_PARAMETER;
  }

  /* Configure DMA. */
  mcu_dma_config_t dma_config;
  if (seq->flags & MCU_I2C_FLAG_READ) {
    /* RX: peripheral to memory. */
    dma_config.src_addr = (void*)&(i2c->RXDR);
    dma_config.dst_addr = &seq->buf[transfer->buf_index].data[transfer->offset];
    dma_config.length = xfer_size;
    dma_config.direction = MCU_DMA_DIR_P2M;
    dma_config.src_width = MCU_DMA_SIZE_1_BYTE;
    dma_config.dst_width = MCU_DMA_SIZE_1_BYTE;
    dma_config.src_increment = false;
    dma_config.dst_increment = true;
    dma_config.request = dma_request_rx;
    dma_config.mode = MCU_DMA_MODE_BASIC;
    dma_config.priority = MCU_DMA_REQ_PRIORITY_MEDIUM;
    dma_config.callback = _mcu_i2c_dma_callback;
    dma_config.user_param = (void*)state;

    const mcu_err_t err = mcu_dma_channel_configure(state->dma.rx, &dma_config);
    if (err != MCU_ERROR_OK) {
      return err;
    }

    /* Enable I2C RX DMA. */
    LL_I2C_EnableDMAReq_RX(i2c);

    /* Start DMA channel. */
    return mcu_dma_channel_start(state->dma.rx);
  } else {
    /* TX: memory to peripheral. */
    dma_config.src_addr = &seq->buf[transfer->buf_index].data[transfer->offset];
    dma_config.dst_addr = (void*)&(i2c->TXDR);
    dma_config.length = xfer_size;
    dma_config.direction = MCU_DMA_DIR_M2P;
    dma_config.src_width = MCU_DMA_SIZE_1_BYTE;
    dma_config.dst_width = MCU_DMA_SIZE_1_BYTE;
    dma_config.src_increment = true;
    dma_config.dst_increment = false;
    dma_config.request = dma_request_tx;
    dma_config.mode = MCU_DMA_MODE_BASIC;
    dma_config.priority = MCU_DMA_REQ_PRIORITY_MEDIUM;
    dma_config.callback = _mcu_i2c_dma_callback;
    dma_config.user_param = (void*)state;

    const mcu_err_t err = mcu_dma_channel_configure(state->dma.tx, &dma_config);
    if (err != MCU_ERROR_OK) {
      return err;
    }

    /* Enable I2C TX DMA. */
    LL_I2C_EnableDMAReq_TX(i2c);

    /* Start DMA channel. */
    return mcu_dma_channel_start(state->dma.tx);
  }
}

static mcu_err_t _mcu_i2c_configure_next_transfer(mcu_i2c_bus_state_t* state,
                                                  mcu_i2c_transfer_t* transfer, I2C_TypeDef* i2c) {
  ASSERT(state != NULL);
  ASSERT(transfer != NULL);
  ASSERT(i2c != NULL);

  /* Update transfer state. */
  const uint32_t current_buf_len = transfer->seq->buf[transfer->buf_index].len;
  const uint32_t transferred = BLK_MIN(current_buf_len - transfer->offset, MCU_I2C_MAX_XFER_SIZE);
  transfer->offset += transferred;
  transfer->remaining -= transferred;

  /* Determine how many bytes remaining in the current buffer. */
  uint32_t remaining_in_buf =
    (transfer->offset >= current_buf_len) ? 0 : (current_buf_len - transfer->offset);

  /* Check if the current buffer has been depleted. */
  if (remaining_in_buf == 0) {
    const uint8_t next_buf_index = transfer->buf_index + 1;

    /* Only update if we have more buffers remaining. */
    if (next_buf_index < ARRAY_SIZE(transfer->seq->buf)) {
      /* Move onto the next buffer. */
      transfer->buf_index = next_buf_index;
      transfer->offset = 0;
      remaining_in_buf = transfer->seq->buf[transfer->buf_index].len;
    }
  }

  /* Configure next transfer size. */
  const uint32_t next_size = BLK_MIN(remaining_in_buf, MCU_I2C_MAX_XFER_SIZE);
  LL_I2C_SetTransferSize(i2c, next_size);

  if (transfer->remaining > next_size) {
    LL_I2C_EnableReloadMode(i2c);
  } else {
    LL_I2C_DisableReloadMode(i2c);
  }

  if (transfer->remaining > 0) {
    /* Configure DMA for the next transfer. */
    const mcu_err_t err = _mcu_i2c_start_dma_transfer(state, transfer);
    if (err != MCU_ERROR_OK) {
      return err;
    }
  }
  return MCU_ERROR_OK;
}

static void _mcu_i2c_disable_interrupts(I2C_TypeDef* i2c) {
  LL_I2C_DisableIT_TC(i2c);
  LL_I2C_DisableIT_NACK(i2c);
  LL_I2C_DisableIT_ERR(i2c);
}

static void _mcu_i2c_post_event(mcu_i2c_bus_state_t* state, mcu_i2c_transfer_event_t event) {
  ASSERT(state != NULL);
#if IMAGE_TYPE_APPLICATION
  bool woken;
  rtos_event_group_set_bits_from_isr(&state->events, event, &woken);
  (void)woken;
#else
  state->events = event;
#endif
}

static uint32_t _mcu_i2c_pend_events(mcu_i2c_bus_state_t* state, uint32_t events,
                                     uint32_t timeout_ms) {
  ASSERT(state != NULL);
#if IMAGE_TYPE_APPLICATION
  const uint32_t bits = rtos_event_group_wait_bits(&state->events, events, true /* clear */,
                                                   false /* wait all */, timeout_ms);
#else
  uint64_t timeout_ticks = timeout_ms * 1000U;
  while (((state->events & events) == 0) && (timeout_ticks > 0)) {
    timeout_ticks--;
  }
  const uint32_t bits = (const uint32_t)state->events;
  state->events = 0;
#endif
  return bits;
}

void I2C1_EV_IRQHandler(void) {
  _mcu_i2c_int_handler(I2C1);
}

void I2C1_ER_IRQHandler(void) {
  _mcu_i2c_err_handler(I2C1);
}

void I2C2_EV_IRQHandler(void) {
  _mcu_i2c_int_handler(I2C2);
}

void I2C2_ER_IRQHandler(void) {
  _mcu_i2c_err_handler(I2C2);
}
