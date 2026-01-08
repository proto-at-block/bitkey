#pragma once

#include "mcu.h"
#include "mcu_gpio.h"
#include "perf.h"
#include "rtos.h"

#include <stdbool.h>
#include <stdint.h>

#define SPI_MAX_TRANSFER_SIZE (32 * 1024) /* Maximum transfer size in bytes */

/* QSPI interface modes */
typedef enum {
  MCU_QSPI_MODE_SPI,  /* 1-line mode (standard SPI) */
  MCU_QSPI_MODE_DUAL, /* 2-line mode (dual SPI) */
  MCU_QSPI_MODE_QUAD, /* 4-line mode (quad SPI) */
} mcu_qspi_mode_e;

/* QSPI peripheral configuration */
typedef struct {
  void* port;              /* OCTOSPI instance pointer */
  mcu_gpio_config_t clk;   /* Clock signal pin */
  mcu_gpio_config_t cs;    /* Chip select pin */
  mcu_gpio_config_t io0;   /* Data line 0 (MOSI in SPI mode) */
  mcu_gpio_config_t io1;   /* Data line 1 (MISO in SPI mode) */
  mcu_gpio_config_t io2;   /* Data line 2 (used in Quad mode) */
  mcu_gpio_config_t io3;   /* Data line 3 (used in Quad mode) */
  uint32_t fifo_threshold; /* FIFO threshold level (1-32 bytes) */
  uint32_t cs_high_time;   /* Chip select high time (1-8 clock cycles) */
  mcu_qspi_mode_e mode;    /* Interface mode (SPI/Dual/Quad) */
  bool sample_shifting;    /* Enable sampling delay (1/2 cycle shift) */
} mcu_qspi_config_t;

/* Forward declaration for callback */
struct mcu_qspi_state;

/* Callback function type for async operations */
typedef void (*mcu_qspi_callback_t)(struct mcu_qspi_state* state, mcu_err_t transfer_status,
                                    uint32_t items_transferred);

/* QSPI state management structure */
typedef struct mcu_qspi_state {
  void* instance;            /* Internal handle to OCTOSPI instance */
  mcu_qspi_config_t* config; /* Pointer to configuration */
  mcu_err_t transfer_status; /* Status of last transfer */
  volatile enum { MCU_QSPI_STATE_IDLE = 0, MCU_QSPI_STATE_BUSY = 1 } state;
  uint32_t transfer_count;      /* Total bytes to transfer */
  mcu_qspi_callback_t callback; /* Async completion callback */

  /* Transfer buffers for async operations */
  uint8_t* tx_buffer;      /* Transmit data buffer */
  uint8_t* rx_buffer;      /* Receive data buffer */
  uint32_t transfer_index; /* Current byte index in transfer */

  /* DMA support */
  uint32_t dma_channel; /* Allocated DMA channel */
  bool dma_enabled;     /* Whether DMA is enabled */

  /* DMA chaining for large transfers */
  uint32_t total_transfer_size; /* Original transfer size for chained transfers */
  uint32_t chunks_remaining;    /* Number of DMA chunks remaining */
  uint32_t current_chunk_size;  /* Size of current DMA chunk */
  uint32_t bytes_transferred;   /* Total bytes transferred so far */

  /* RTOS synchronization primitives */
  rtos_mutex_t access;                /* Mutex for thread-safe access */
  rtos_semaphore_t transfer_complete; /* Semaphore for blocking transfers */
} mcu_qspi_state_t;

/* Command structure for QSPI operations */
typedef struct {
  /* Instruction phase */
  uint8_t instruction;              /* Command opcode byte */
  bool instruction_enable;          /* Enable instruction phase */
  mcu_qspi_mode_e instruction_mode; /* Line mode for instruction */

  /* Address phase */
  uint32_t address;             /* Target address */
  uint8_t address_size;         /* Address size in bytes (0-4) */
  mcu_qspi_mode_e address_mode; /* Line mode for address */

  /* Alternate bytes phase (for mode bits, etc.) */
  uint32_t alternate_bytes;       /* Alternate bytes value */
  uint8_t alternate_bytes_size;   /* Size in bytes (0-4) */
  mcu_qspi_mode_e alternate_mode; /* Line mode for alternate bytes */

  /* Dummy cycles */
  uint8_t dummy_cycles; /* Number of dummy clock cycles */

  /* Data phase */
  uint32_t data_length;      /* Number of bytes to transfer */
  mcu_qspi_mode_e data_mode; /* Line mode for data */
} mcu_qspi_command_t;

/*
 * Initialize QSPI peripheral with specified configuration.
 * Must be called before any other QSPI operations.
 */
mcu_err_t mcu_qspi_init(mcu_qspi_state_t* state, mcu_qspi_config_t* config);

/*
 * Execute blocking QSPI command with optional data transfer.
 * For transmit: tx_data must be non-NULL, rx_data must be NULL.
 * For receive: tx_data must be NULL, rx_data must be non-NULL.
 * For command-only: both tx_data and rx_data must be NULL.
 */
mcu_err_t mcu_qspi_command(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd, const uint8_t* tx_data,
                           uint8_t* rx_data);

/*
 * Execute asynchronous QSPI command with optional callback.
 * If callback is NULL, function blocks until transfer completes.
 * If callback is provided, function returns immediately and callback
 * is invoked from interrupt context when transfer completes.
 */
mcu_err_t mcu_qspi_command_async(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd,
                                 const uint8_t* tx_data, uint8_t* rx_data,
                                 mcu_qspi_callback_t callback);

/*
 * Execute asynchronous QSPI command with DMA.
 * If DMA is not available, falls back to interrupt-driven transfer.
 * If callback is NULL, function blocks until transfer completes.
 * If callback is provided, function returns immediately and callback
 * is invoked from DMA interrupt context when transfer completes.
 */
mcu_err_t mcu_qspi_command_async_dma(mcu_qspi_state_t* state, mcu_qspi_command_t* cmd,
                                     const uint8_t* tx_data, uint8_t* rx_data,
                                     mcu_qspi_callback_t callback);

/*
 * Abort current QSPI operation.
 * Forces the peripheral to stop any ongoing transfer.
 */
mcu_err_t mcu_qspi_abort(mcu_qspi_state_t* state);
