#include "gfx.h"

#include "assert.h"
#include "exti.h"
#include "log.h"
#include "mcu_gpio.h"
#include "mcu_qspi.h"
#include "rtos.h"

#include <string.h>

static mcu_qspi_state_t display_qspi_state = {0};
static exti_config_t te_exti_config = {0};
static bool te_has_pulse = false;
static uint64_t te_last_pulse_us = 0;
static uint16_t display_width = 0;
static uint16_t display_height = 0;

// FPS tracking with circular buffer
#define FLUSH_HISTORY_SIZE 50
typedef struct {
  uint32_t timestamp_ms;
  uint32_t pixel_count;
} pixel_sample_t;
static pixel_sample_t fps_samples[FLUSH_HISTORY_SIZE] = {0};
static uint8_t fps_idx = 0;
static rtos_mutex_t fps_mutex = {0};

#define TE_WINDOW_US            16000  // Allowed flush window from falling to rising edge
#define TE_WAIT_TIMEOUT_MS      18     // Timeout for single TE wait
#define TE_PREPARE_MAX_ATTEMPTS 2      // Max attempts to find suitable window (initial + 1 retry)
#define TE_MARGIN_US            500    // Safety margin before window ends
#define FLUSH_TIME_STATIC_US    1300   // Fixed overhead per flush
#define FLUSH_BYTES_PER_US      35     // Transfer rate

// ILI8688F command definitions
#define QSPI_CMD_WRITE       0x02
#define QSPI_CMD_READ        0x03
#define QSPI_CMD_WRITE_4WIRE 0x32

#define MAX_QSPI_READ_BYTES 4
#define QUAD_BUFFER_SIZE    (MAX_QSPI_READ_BYTES * 4)

// Page 0 command definitions
#define CMD_SWRESET     0x01
#define CMD_SLPOUT      0x11
#define CMD_ALLPON      0x23
#define CMD_DISPON      0x29
#define CMD_CASET       0x2A
#define CMD_RASET       0x2B
#define CMD_RAMWR       0x2C
#define CMD_TEOFF       0x34
#define CMD_TEON        0x35
#define CMD_MADCTL      0x36
#define CMD_COLMOD      0x3A
#define CMD_WRDISBV     0x51
#define CMD_WRCTRLD     0x53
#define CMD_SETDSPIMODE 0xC4
// Display ID register addresses (page 0)
#define DISPLAY_REG_MANUFACTURER_ID 0xDA
#define DISPLAY_REG_DISPLAY_ID      0xDB
#define DISPLAY_REG_REVISION_ID     0xDC

// Display hardware revisions
typedef enum {
  DISPLAY_HW_EVT_PDVT,  // Factory default, OTP not programmed
  DISPLAY_HW_DVT,       // OTP programmed by Tianma
  DISPLAY_HW_UNKNOWN
} display_hw_revision_t;

// EVT/PDVT
#define DISPLAY_ID_EVT_PDVT_MFR 0x00
#define DISPLAY_ID_EVT_PDVT_DIS 0x80
#define DISPLAY_ID_EVT_PDVT_REV 0x00

// DVT (programmed by Tianma)
#define DISPLAY_ID_DVT_MFR 0x02
#define DISPLAY_ID_DVT_DIS 0x82  // Bit 7 unchangeable per datasheet
#define DISPLAY_ID_DVT_REV 0x01

// MADCTL (Memory Data Access Control) register bits:
#define MADCTL_MY  (1 << 7)  // Page Address Order (0=top-to-bottom, 1=bottom-to-top)
#define MADCTL_MX  (1 << 6)  // Column Address Order (0=left-to-right, 1=right-to-left)
#define MADCTL_BGR (1 << 3)  // RGB/BGR Order (0=RGB, 1=BGR)

#define ICNA_RESET_PULSE_MS 10
#define ICNA_RESET_DELAY_MS 120

typedef struct {
  gfx_flush_complete_cb_t user_cb;
  void* user_data;
} gfx_flush_ctx_t;
static gfx_flush_ctx_t s_flush_ctx;

// Display register configuration for table-driven initialization
typedef struct {
  uint8_t page;       // Page number (0, 1, 6, 7, etc.)
  uint8_t reg;        // Register address
  uint8_t data[4];    // Data bytes (up to 4)
  uint8_t len;        // Number of data bytes (0 for command-only)
  uint16_t delay_ms;  // Optional delay after write (0 = no delay)
  bool verify;        // Whether to read back and verify (single-byte only)
} display_reg_config_t;

// OTP configuration table (only for EVT/PDVT displays without programmed OTP)
static display_reg_config_t display_otp_config_table[] = {
  // Page 6 registers
  {.page = 0x06, .reg = 0x3E, .data = {0xE2}, .len = 0x01, .delay_ms = 1, .verify = true},

  // Page 0x0E registers (OTP settings missing on EVT displays, configured here instead)
  {.page = 0x0E, .reg = 0x0B, .data = {0x00}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x92, .data = {0x7E}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x94, .data = {0xFD}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x95, .data = {0x7C}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x97, .data = {0xFB}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x98, .data = {0x83}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x9A, .data = {0x12}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x9B, .data = {0xA9}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x9D, .data = {0x40}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0x9E, .data = {0xC7}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA0, .data = {0x4E}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA1, .data = {0xD5}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA3, .data = {0x6C}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA4, .data = {0x04}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA6, .data = {0xA3}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA7, .data = {0x4A}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA8, .data = {0x00}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xA9, .data = {0xF9}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xAA, .data = {0xB1}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xAC, .data = {0x78}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xAD, .data = {0x50}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xAF, .data = {0x38}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xB0, .data = {0x20}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xB2, .data = {0x10}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xB3, .data = {0x08}, .len = 0x01, .delay_ms = 1, .verify = true},
  {.page = 0x0E, .reg = 0xB5, .data = {0x00}, .len = 0x01, .delay_ms = 1, .verify = true},
};

static const size_t display_otp_config_table_size =
  sizeof(display_otp_config_table) / sizeof(display_otp_config_table[0]);

// Common initialization register table (for all display revisions)
static display_reg_config_t display_init_table[] = {
  // Exit sleep mode first (requires 120ms delay)
  {.page = 0x00, .reg = CMD_SLPOUT, .data = {0x00}, .len = 0x00, .delay_ms = 120, .verify = true},

  // Set SPI Write RAM Enable
  {.page = 0x00,
   .reg = CMD_SETDSPIMODE,
   .data = {0x80},
   .len = 0x01,
   .delay_ms = 1,
   .verify = true},

  // Set interface pixel format to RGB565 (16-bit)
  {.page = 0x00, .reg = CMD_COLMOD, .data = {0x55}, .len = 0x01, .delay_ms = 1, .verify = false},

  // Enable brightness control and backlight control
  {.page = 0x00, .reg = CMD_WRCTRLD, .data = {0x24}, .len = 0x01, .delay_ms = 1, .verify = true},

  // Set maximum brightness level (Normal and AOD)
  {.page = 0x00,
   .reg = CMD_WRDISBV,
   .data = {0xFF, 0x00},
   .len = 0x02,
   .delay_ms = 1,
   .verify = true},

  // Set display orientation + RGB order (0x00 for normal, updated at runtime based on board_id)
  {.page = 0x00, .reg = CMD_MADCTL, .data = {0x00}, .len = 0x01, .delay_ms = 1, .verify = true},

  // Turn on tearing effect signal (mode 0)
  {.page = 0x00, .reg = CMD_TEON, .data = {0x00}, .len = 0x01, .delay_ms = 1, .verify = true},

  // Turn on display (requires 5ms delay)
  {.page = 0x00, .reg = CMD_DISPON, .data = {0x00}, .len = 0x00, .delay_ms = 5, .verify = true},
};

static const size_t display_init_table_size =
  sizeof(display_init_table) / sizeof(display_init_table[0]);

static void write_reg(uint8_t reg, const uint8_t* params, size_t param_len) {
  mcu_qspi_command_t cmd = {0};

  // Validate param_len (display typically accepts up to 4 parameter bytes)
  if (param_len > 4) {
    LOGE("QSPI write reg %02X: param_len %zu exceeds max 4", reg, param_len);
    return;
  }

  // Instruction phase: QSPI write command (0x02)
  cmd.instruction = QSPI_CMD_WRITE;
  cmd.instruction_enable = true;
  cmd.instruction_mode = MCU_QSPI_MODE_SPI;

  // Address phase: [0x00][REG][0x00] - 24-bit sequence
  cmd.address = (0x00 << 16) | (reg << 8) | 0x00;
  cmd.address_size = 3;  // 24-bit address
  cmd.address_mode = MCU_QSPI_MODE_SPI;

  // No alternate bytes phase
  cmd.alternate_bytes_size = 0;

  // No dummy cycles
  cmd.dummy_cycles = 0;

  // Data phase: configuration parameters (if any)
  if (param_len > 0 && params != NULL) {
    cmd.data_length = param_len;
    cmd.data_mode = MCU_QSPI_MODE_SPI;
  } else {
    cmd.data_length = 0;  // Command only, no data phase
  }

  mcu_err_t res = mcu_qspi_command(&display_qspi_state, &cmd, params, NULL);
  if (res != MCU_ERROR_OK) {
    LOGE("QSPI write reg %02X failed: %d", reg, res);
  }
}

static bool read_reg(uint8_t reg, uint8_t* rx_buffer, size_t rx_len) {
  if (rx_buffer == NULL || rx_len == 0 || rx_len > MAX_QSPI_READ_BYTES) {
    LOGE("QSPI read reg %02X: invalid parameters", reg);
    return false;
  }

  mcu_qspi_command_t cmd = {0};

  // Instruction phase: QSPI read command (0x03)
  cmd.instruction = QSPI_CMD_READ;  // 0x03
  cmd.instruction_enable = true;
  cmd.instruction_mode = MCU_QSPI_MODE_SPI;

  // Address phase: [0x00][REG][0x00]
  cmd.address = (0x00 << 16) | (reg << 8) | 0x00;
  cmd.address_size = 3;
  cmd.address_mode = MCU_QSPI_MODE_SPI;

  // Dummy cycle required for read
  cmd.dummy_cycles = 1;

  // WORKAROUND: The ILI8688F display controller has non-standard SPI read behavior:
  // - It accepts standard single-wire SPI read commands (0x03 on D0)
  // - BUT it sends data back on D0 (bidirectional) instead of D1 (MISO)
  // - This violates standard SPI protocol where MOSI=D0 (out) and MISO=D1 (in)
  // Since the display sends data on D0 instead of D1, we use this workaround:
  // 1. Send command/address in single-wire SPI mode (on D0)
  // 2. Read data in QUAD mode (reads all 4 lines: D0, D1, D2, D3)
  // 3. Extract only the D0 bits from the QUAD data

  // Data phase: Read in QUAD mode (4x the data to extract D0 bits later)
  uint8_t quad_buffer[QUAD_BUFFER_SIZE] = {0};
  cmd.data_length = rx_len * 4;        // Read 4x bytes to get all D0 bits
  cmd.data_mode = MCU_QSPI_MODE_QUAD;  // 4-wire QSPI mode - all lines bidirectional

  mcu_err_t res = mcu_qspi_command(&display_qspi_state, &cmd, NULL, quad_buffer);
  if (res != MCU_ERROR_OK) {
    LOGE("QSPI read reg 0x%02X failed with error %d", reg, res);
    return false;
  }

  // Extract D0 line data from QUAD SPI read
  // QUAD SPI reads 4 lines (D0-D3) simultaneously each clock cycle.
  // Each QUAD byte stores 2 clock cycles worth of 4-bit samples:
  //   Byte format: [D3_clk1 D2_clk1 D1_clk1 D0_clk1 | D3_clk0 D2_clk0 D1_clk0 D0_clk0]
  //   Bit positions: 7       6       5       4       | 3       2       1       0
  // To reconstruct 1 output byte from the D0 line only:
  //   - We need 8 bits from D0 = 8 clock cycles of D0 data
  //   - 8 clock cycles span 4 QUAD bytes (2 clocks per QUAD byte)
  //   - D0 data is at bit position 0 (even clocks) and bit 4 (odd clocks)
  for (size_t i = 0; i < rx_len; i++) {
    uint8_t result = 0;

    // Extract 8 D0 bits from 4 QUAD bytes (8 clock cycles)
    for (int clock = 0; clock < 8; clock++) {
      int quad_byte = i * 4 + clock / 2;  // 2 clocks per QUAD byte
      int bit_pos = (clock & 1) ? 0 : 4;  // Even clocks→bit 4, odd clocks→bit 0

      // Extract D0 bit from this clock cycle and place in result (MSB first)
      if (quad_buffer[quad_byte] & (1 << bit_pos)) {
        result |= (1 << (7 - clock));
      }
    }

    rx_buffer[i] = result;
  }

  return true;
}

static bool gfx_wait_for_te_pulse(void) {
  exti_clear(&te_exti_config);
  if (!exti_wait(&te_exti_config, TE_WAIT_TIMEOUT_MS, true)) {
    LOGW("TE wait timed out");
    te_has_pulse = false;
    return false;
  }

  te_last_pulse_us = rtos_thread_micros();
  te_has_pulse = true;
  return true;
}

static uint32_t gfx_estimate_flush_time_us(uint32_t total_bytes) {
  return (uint32_t)total_bytes / FLUSH_BYTES_PER_US + FLUSH_TIME_STATIC_US;
}

static bool gfx_prepare_flush_window(uint32_t flush_time_us) {
  if (!te_has_pulse) {
    if (!gfx_wait_for_te_pulse()) {
      return false;
    }
  }

  for (uint32_t attempt = 0; attempt < TE_PREPARE_MAX_ATTEMPTS; attempt++) {
    uint64_t now = rtos_thread_micros();
    uint64_t window_end = te_last_pulse_us + TE_WINDOW_US;

    // Check if we have a usable window
    if (now < window_end) {
      uint64_t available_us = window_end - now;
      if (available_us > TE_MARGIN_US) {
        uint64_t usable_us = available_us - TE_MARGIN_US;
        if (usable_us > flush_time_us) {
          // Window is suitable, proceed with flush
          return true;
        }
      }
    }

    // Window expired, too small, or flush won't fit - wait for next TE
    if (!gfx_wait_for_te_pulse()) {
      return false;
    }
  }

  // Failed to find window after max attempts
  LOGW("TE sync failed after %u attempts", TE_PREPARE_MAX_ATTEMPTS);
  return false;
}

static void set_page(uint8_t page) {
  write_reg(0xFF, (uint8_t[]){0x86, 0x88, page}, 3);
  rtos_thread_sleep(1);
}

static void display_reset(const gfx_config_t* gfx_config) {
  ASSERT(gfx_config != NULL);
  mcu_gpio_output_set(&gfx_config->rst, false);
  rtos_thread_sleep(ICNA_RESET_PULSE_MS);
  mcu_gpio_output_set(&gfx_config->rst, true);
  rtos_thread_sleep(ICNA_RESET_DELAY_MS);
}

// Write registers from a configuration table
static void write_register_table(const display_reg_config_t* table, size_t table_size) {
  uint8_t last_page = 0xFF;

  for (size_t i = 0; i < table_size; i++) {
    const display_reg_config_t* cfg = &table[i];

    // Switch page if needed
    if (cfg->page != last_page) {
      set_page(cfg->page);
      last_page = cfg->page;
    }

    // Write register
    if (cfg->len > 0) {
      write_reg(cfg->reg, cfg->data, cfg->len);
    } else {
      // Command-only (no data)
      write_reg(cfg->reg, NULL, 0);
    }

    // Apply optional delay if specified
    if (cfg->delay_ms > 0) {
      rtos_thread_sleep(cfg->delay_ms);
    }
  }
}

// Verify registers from a configuration table
static void verify_register_table(const display_reg_config_t* table, size_t table_size,
                                  const char* table_name) {
  uint8_t last_page = 0xFF;
  uint32_t pass_count = 0;
  uint32_t fail_count = 0;

  // Enable read mode once
  set_page(0x01);
  write_reg(0xFD, (uint8_t[]){0x00, 0x81, 0x00}, 3);  // Enable read
  rtos_thread_sleep(1);

  // Verify all registers marked for verification
  for (size_t i = 0; i < table_size; i++) {
    const display_reg_config_t* cfg = &table[i];

    // Skip non-verify registers or multi-byte writes
    if (!cfg->verify || cfg->len != 1) {
      continue;
    }

    // Switch page if needed
    if (cfg->page != last_page) {
      set_page(cfg->page);
      last_page = cfg->page;
    }

    // Read back and verify
    uint8_t read_val = 0xFF;
    if (read_reg(cfg->reg, &read_val, 1)) {
      if (read_val == cfg->data[0]) {
        pass_count++;
        // LOGI("P%d Reg 0x%02X: 0x%02X", cfg->page, cfg->reg, read_val);
      } else {
        fail_count++;
        LOGE("%s: P%d Reg 0x%02X: wrote 0x%02X, read 0x%02X", table_name, cfg->page, cfg->reg,
             cfg->data[0], read_val);
      }
    } else {
      fail_count++;
      LOGE("%s: P%d Reg 0x%02X: read failed", table_name, cfg->page, cfg->reg);
    }
  }

  // Disable read mode
  set_page(0x01);
  write_reg(0xFD, (uint8_t[]){0x00, 0x00, 0x00}, 3);  // Disable read
  rtos_thread_sleep(1);

  if (fail_count > 0) {
    LOGW("%s verification: %lu passed, %lu failed", table_name, pass_count, fail_count);
  }
}

static display_hw_revision_t read_display_hw_revision(void) {
  // Enable read mode
  set_page(0x01);
  write_reg(0xFD, (uint8_t[]){0x00, 0x81, 0x00}, 3);
  rtos_thread_sleep(1);

  // Switch to page 0 where ID registers are located
  set_page(0x00);

  // Read ID registers
  uint8_t manufacturer_id = 0xFF;
  uint8_t display_id = 0xFF;
  uint8_t revision_id = 0xFF;

  bool success = true;
  success &= read_reg(DISPLAY_REG_MANUFACTURER_ID, &manufacturer_id, 1);
  success &= read_reg(DISPLAY_REG_DISPLAY_ID, &display_id, 1);
  success &= read_reg(DISPLAY_REG_REVISION_ID, &revision_id, 1);

  // Disable read mode
  set_page(0x01);
  write_reg(0xFD, (uint8_t[]){0x00, 0x00, 0x00}, 3);
  rtos_thread_sleep(1);

  display_hw_revision_t hw_rev = DISPLAY_HW_UNKNOWN;

  if (success) {
    // Identify hardware revision
    if (manufacturer_id == DISPLAY_ID_EVT_PDVT_MFR && display_id == DISPLAY_ID_EVT_PDVT_DIS &&
        revision_id == DISPLAY_ID_EVT_PDVT_REV) {
      hw_rev = DISPLAY_HW_EVT_PDVT;
      LOGI("Display hardware: EVT/PDVT");
    } else if (manufacturer_id == DISPLAY_ID_DVT_MFR && display_id == DISPLAY_ID_DVT_DIS &&
               revision_id == DISPLAY_ID_DVT_REV) {
      hw_rev = DISPLAY_HW_DVT;
      LOGI("Display hardware: DVT");
    } else {
      LOGW("Display hardware: Unknown revision (MFR=0x%02X, ID=0x%02X, REV=0x%02X)",
           manufacturer_id, display_id, revision_id);
    }
  } else {
    LOGE("Failed to read display ID registers");
  }

  return hw_rev;
}

void gfx_init(const gfx_config_t* gfx_config) {
  ASSERT(gfx_config != NULL);
  ASSERT(gfx_config->display_width > 0);
  ASSERT(gfx_config->display_height > 0);

  // Store display resolution
  display_width = gfx_config->display_width;
  display_height = gfx_config->display_height;

  // GPIO for RST
  mcu_gpio_set_mode(&gfx_config->rst, MCU_GPIO_MODE_OUTPUT, false);

  // QSPI init
  mcu_err_t res = mcu_qspi_init(&display_qspi_state, gfx_config->display_qspi_config);
  if (res != MCU_ERROR_OK) {
    LOGE("QSPI init failed: %d", res);
    return;
  }

  // TE pin EXTI
  te_exti_config.gpio = gfx_config->te;
  te_exti_config.trigger = EXTI_TRIGGER_FALLING;
  exti_enable(&te_exti_config);
  te_has_pulse = false;
  te_last_pulse_us = 0;

  // Initialize FPS tracking buffer
  memset(fps_samples, 0, sizeof(fps_samples));
  fps_idx = 0;
  rtos_mutex_create(&fps_mutex);

  display_reset(gfx_config);

  // Read display hardware revision to determine init sequence
  display_hw_revision_t hw_rev = read_display_hw_revision();

  // Write OTP config for EVT/PDVT displays (DVT displays have OTP already programmed)
  // For unknown hardware, apply OTP config as a safe fallback
  bool otp_written = false;
  if (hw_rev == DISPLAY_HW_EVT_PDVT || hw_rev == DISPLAY_HW_UNKNOWN) {
    if (hw_rev == DISPLAY_HW_UNKNOWN) {
      LOGW("Unknown display revision, applying OTP config as fallback");
    }
    write_register_table(display_otp_config_table, display_otp_config_table_size);
    otp_written = true;
  }

  // Write common initialization registers for all displays
  write_register_table(display_init_table, display_init_table_size);

  // Verify registers
  if (otp_written) {
    verify_register_table(display_otp_config_table, display_otp_config_table_size, "OTP config");
  }
  verify_register_table(display_init_table, display_init_table_size, "Init");

  set_page(0);  // Return to page 0
}

void gfx_set_brightness(uint8_t level) {
  write_reg(CMD_WRDISBV, (uint8_t[]){level, 0}, 2);
  rtos_thread_sleep(1);
}

void gfx_set_rotation(bool rotate_180) {
  uint8_t madctl_val = rotate_180 ? (MADCTL_MY | MADCTL_MX) : 0x00;
  set_page(0);
  write_reg(CMD_MADCTL, &madctl_val, 1);
  rtos_thread_sleep(1);
}

static void flush_complete(mcu_qspi_state_t* state, mcu_err_t status, uint32_t sent) {
  (void)state;
  (void)sent;

  if (status != MCU_ERROR_OK) {
    LOGE("QSPI flush failed: %d", status);
  }

  // Call user callback on completion
  if (s_flush_ctx.user_cb) {
    s_flush_ctx.user_cb(s_flush_ctx.user_data);
  }
}

void gfx_flush(uint8_t* buffer, uint16_t x1, uint16_t y1, uint16_t x2, uint16_t y2,
               gfx_flush_complete_cb_t callback, void* user_data) {
  uint32_t w = x2 - x1 + 1;
  uint32_t h = y2 - y1 + 1;
  uint32_t total_pixels = w * h;
  uint32_t total_bytes = total_pixels * 2;  // RGB565 = 2 bytes per pixel
  uint32_t flush_time_us = gfx_estimate_flush_time_us(total_bytes);

  bool te_ready = gfx_prepare_flush_window(flush_time_us);
  if (!te_ready) {
    LOGW("Proceeding without TE sync");
  }

  // Track flush rate and pixel count in circular buffer
  rtos_mutex_lock(&fps_mutex);
  fps_samples[fps_idx].timestamp_ms = (uint32_t)(rtos_thread_micros() / 1000);
  fps_samples[fps_idx].pixel_count = total_pixels;
  fps_idx = (fps_idx + 1) % FLUSH_HISTORY_SIZE;
  rtos_mutex_unlock(&fps_mutex);

  // Setup flush context
  s_flush_ctx.user_cb = callback;
  s_flush_ctx.user_data = user_data;

  // column/row set - using 1-wire SPI for commands
  uint8_t col_addr[] = {(uint8_t)(x1 >> 8), (uint8_t)x1, (uint8_t)(x2 >> 8), (uint8_t)x2};
  write_reg(CMD_CASET, col_addr, sizeof(col_addr));

  uint8_t row_addr[] = {(uint8_t)(y1 >> 8), (uint8_t)y1, (uint8_t)(y2 >> 8), (uint8_t)y2};
  write_reg(CMD_RASET, row_addr, sizeof(row_addr));

  // Send RAMWR command with all pixel data using DMA
  mcu_qspi_command_t cmd = {0};
  // Instruction phase
  cmd.instruction = QSPI_CMD_WRITE_4WIRE;
  cmd.instruction_enable = true;
  cmd.instruction_mode = MCU_QSPI_MODE_SPI;  // 1-line for instruction
  // Address phase - [0x00][RAMWR][0x00]
  cmd.address = (0x00 << 16) | (CMD_RAMWR << 8) | 0x00;
  cmd.address_size = 3;                  // 3 bytes
  cmd.address_mode = MCU_QSPI_MODE_SPI;  // 1-line for address
  // No dummy cycles
  cmd.dummy_cycles = 0;
  // Data phase - all pixel data in quad mode
  cmd.data_mode = MCU_QSPI_MODE_QUAD;  // 4-line for data
  cmd.data_length = total_bytes;

  // Queue the entire transfer and call user callback on completion
  mcu_qspi_command_async_dma(&display_qspi_state, &cmd, buffer, NULL, flush_complete);
}

uint32_t gfx_get_fps(void) {
  rtos_mutex_lock(&fps_mutex);

  // Find oldest and newest valid timestamps in circular buffer
  uint32_t oldest_time = 0;
  uint32_t newest_time = 0;
  uint32_t count = 0;

  for (uint8_t i = 0; i < FLUSH_HISTORY_SIZE; i++) {
    if (fps_samples[i].timestamp_ms > 0) {
      if (oldest_time == 0 || fps_samples[i].timestamp_ms < oldest_time) {
        oldest_time = fps_samples[i].timestamp_ms;
      }
      if (fps_samples[i].timestamp_ms > newest_time) {
        newest_time = fps_samples[i].timestamp_ms;
      }
      count++;
    }
  }

  rtos_mutex_unlock(&fps_mutex);

  if (count < 2) {
    return 0;
  }

  uint32_t duration_ms = newest_time - oldest_time;
  if (duration_ms == 0) {
    return 0;
  }

  const uint32_t fps = ((count - 1) * (1000 * 1000)) / duration_ms;
  return (fps + 500) / 1000;
}

uint32_t gfx_get_effective_fps(void) {
  if (display_width == 0 || display_height == 0) {
    return 0;
  }

  rtos_mutex_lock(&fps_mutex);

  uint32_t oldest_time = 0;
  uint32_t newest_time = 0;
  uint64_t total_pixels = 0;

  for (uint8_t i = 0; i < FLUSH_HISTORY_SIZE; i++) {
    if (fps_samples[i].timestamp_ms > 0) {
      if (oldest_time == 0 || fps_samples[i].timestamp_ms < oldest_time) {
        oldest_time = fps_samples[i].timestamp_ms;
      }
      if (fps_samples[i].timestamp_ms > newest_time) {
        newest_time = fps_samples[i].timestamp_ms;
      }
      total_pixels += fps_samples[i].pixel_count;
    }
  }

  rtos_mutex_unlock(&fps_mutex);

  uint32_t duration_ms = newest_time - oldest_time;
  if (duration_ms < 100 || total_pixels == 0) {
    return 0;
  }

  const uint32_t pixels_per_frame = (uint32_t)display_width * (uint32_t)display_height;
  if (pixels_per_frame == 0) {
    return 0;
  }

  const uint64_t effective_fps = (total_pixels * 1000 * 1000) / (duration_ms * pixels_per_frame);
  return (uint32_t)((effective_fps + 500) / 1000);
}
