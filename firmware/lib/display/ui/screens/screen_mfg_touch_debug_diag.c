/**
 * @file screen_mfg_touch_debug_diag.c
 * @brief Touch debug diagnostic modes: Force FWUP, Collect Data, Display Test.
 */

#include "screen_mfg_touch_debug_internal.h"

#ifdef MFGTEST

#include "lvgl/lvgl.h"
#include "printf.h"
#include "ui.h"
#include "widgets/mfg_burnin_grid.h"
#include "widgets/mfg_starfield_fps.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

// ============================================================================
// Force FWUP static state
// ============================================================================
static lv_obj_t* fwup_status_label = NULL;
static lv_obj_t* fwup_start_btn = NULL;
static lv_obj_t* fwup_progress_label = NULL;
static bool fwup_running = false;

// ============================================================================
// Collect Data static state
// ============================================================================
static lv_obj_t* collect_status_label = NULL;

// Collection states
typedef enum {
  COLLECT_STATE_IDLE,
  COLLECT_STATE_COUNTDOWN,
  COLLECT_STATE_COLLECTING,
  COLLECT_STATE_DONE
} collect_state_t;

static collect_state_t collect_state = COLLECT_STATE_IDLE;
static lv_timer_t* collect_countdown_timer = NULL;
static int collect_countdown_value = 0;

// Collection output step - which section to output next
static int collect_output_step = 0;
static lv_timer_t* collect_output_timer = NULL;
static bool collect_in_factory_mode = false;

// ============================================================================
// Display Test static state
// ============================================================================
typedef enum {
  DISP_TEST_RED = 0,
  DISP_TEST_GREEN,
  DISP_TEST_BLUE,
  DISP_TEST_WHITE,
  DISP_TEST_BLACK,
  DISP_TEST_BURNIN_GRID,
  DISP_TEST_STARFIELD,
  DISP_TEST_COUNT
} disp_test_mode_t;

static disp_test_mode_t disp_test_current = DISP_TEST_RED;
static mfg_starfield_fps_t disp_test_starfield = {0};
static const char* disp_test_labels[DISP_TEST_COUNT] = {
  "RED", "GREEN", "BLUE", "WHITE", "BLACK", "BURN-IN GRID", "STARFIELD"};
static const uint32_t disp_test_colors[DISP_TEST_COUNT] = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF,
                                                           0x000000, 0x000000, 0x000000};

// ============================================================================
// Force FW Update Implementation
// ============================================================================

// Button handler for "Force Update" button
static void fwup_start_btn_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  if (fwup_running) {
    return;  // Already running
  }

  fwup_running = true;

  // Hide the start button
  if (fwup_start_btn != NULL) {
    lv_obj_add_flag(fwup_start_btn, LV_OBJ_FLAG_HIDDEN);
  }

  // Update status
  if (fwup_status_label != NULL) {
    lv_label_set_text(fwup_status_label, "Updating...");
    lv_obj_set_style_text_color(fwup_status_label, lv_color_hex(0xFFFF00), 0);
  }

  if (fwup_progress_label != NULL) {
    lv_label_set_text(fwup_progress_label, "DO NOT POWER OFF!");
    lv_obj_set_style_text_color(fwup_progress_label, lv_color_hex(0xFF6600), 0);
  }

  // Force UI update before blocking call
  lv_timer_handler();

  printf("Force FW Update: Starting touch_fwup_force_upgrade_with_ecc()...\r\n");

  // Pause ESD checks during firmware upgrade to prevent I2C conflicts
  touch_set_fwup_in_progress(true);

  // Call the force upgrade function with ECC reporting (this may take several seconds)
  touch_fwup_result_t result = {0};
  bool success = touch_fwup_force_upgrade_with_ecc(&result);

  // Re-enable ESD checks
  touch_set_fwup_in_progress(false);

  printf("Force FW Update: Result = %s\r\n", success ? "SUCCESS" : "FAILED");
  if (success) {
    printf("  Host ECC:   0x%04X\r\n", result.ecc_host);
    printf("  Device ECC: 0x%04X\r\n", result.ecc_device);
    printf("  FW Version: 0x%02X\r\n", result.fw_version);
  }

  // Update UI with result
  if (fwup_status_label != NULL) {
    if (success) {
      lv_label_set_text(fwup_status_label, "SUCCESS!");
      lv_obj_set_style_text_color(fwup_status_label, lv_color_hex(0x00FF00), 0);
    } else {
      lv_label_set_text(fwup_status_label, "FAILED!");
      lv_obj_set_style_text_color(fwup_status_label, lv_color_hex(0xFF0000), 0);
    }
  }

  if (fwup_progress_label != NULL) {
    char buf[64];
    if (success) {
      // Show ECC values and new firmware version
      snprintf(buf, sizeof(buf), "FW:0x%02X ECC:0x%04X", result.fw_version, result.ecc_host);
      lv_label_set_text(fwup_progress_label, buf);
      lv_obj_set_style_text_color(fwup_progress_label, lv_color_hex(0x00FFFF), 0);
      // Update cached version
      td_touch_fw_version = result.fw_version;
      td_touch_fw_version_read = true;
    } else {
      lv_label_set_text(fwup_progress_label, "Upgrade failed - check UART");
      lv_obj_set_style_text_color(fwup_progress_label, lv_color_hex(0xFF6600), 0);
    }
  }

  // Show the button again
  if (fwup_start_btn != NULL) {
    lv_obj_clear_flag(fwup_start_btn, LV_OBJ_FLAG_HIDDEN);
    // Update button text
    lv_obj_t* btn_label = lv_obj_get_child(fwup_start_btn, 0);
    if (btn_label != NULL) {
      lv_label_set_text(btn_label, "Update Again");
    }
  }

  fwup_running = false;
}

// Show the Force FW Update screen
void td_show_force_fwup(void) {
  td_clear_screen_content();
  td_current_mode = MODE_FORCE_FWUP;

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Force FW Update");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0xFF00FF), 0);  // Magenta to stand out
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_24, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 45);

  // Current version display
  lv_obj_t* ver_label = lv_label_create(td_screen);
  if (td_touch_fw_version_read) {
    char ver_buf[32];
    snprintf(ver_buf, sizeof(ver_buf), "Current: 0x%02X (%d)", td_touch_fw_version,
             td_touch_fw_version);
    lv_label_set_text(ver_label, ver_buf);
  } else {
    lv_label_set_text(ver_label, "Current: Unknown");
  }
  lv_obj_set_style_text_color(ver_label, lv_color_hex(0x00FFFF), 0);
  lv_obj_set_style_text_font(ver_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(ver_label, LV_ALIGN_TOP_MID, 0, 78);

  // Description
  lv_obj_t* desc_label = lv_label_create(td_screen);
  lv_label_set_text(desc_label, "Forces touch FW flash\neven if version matches.");
  lv_obj_set_style_text_color(desc_label, lv_color_hex(0xAAAAAA), 0);
  lv_obj_set_style_text_font(desc_label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(desc_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(desc_label, LV_ALIGN_TOP_MID, 0, 110);

  // Status label
  fwup_status_label = lv_label_create(td_screen);
  lv_label_set_text(fwup_status_label, "Ready");
  lv_obj_set_style_text_color(fwup_status_label, lv_color_hex(0x888888), 0);
  lv_obj_set_style_text_font(fwup_status_label, &cash_sans_mono_regular_28, 0);
  lv_obj_align(fwup_status_label, LV_ALIGN_CENTER, 0, 20);

  // Progress/info label
  fwup_progress_label = lv_label_create(td_screen);
  lv_label_set_text(fwup_progress_label, "");
  lv_obj_set_style_text_color(fwup_progress_label, lv_color_hex(0x888888), 0);
  lv_obj_set_style_text_font(fwup_progress_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(fwup_progress_label, LV_ALIGN_CENTER, 0, 55);

  // Force Update button
  fwup_start_btn = lv_btn_create(td_screen);
  lv_obj_set_size(fwup_start_btn, 180, 60);
  lv_obj_align(fwup_start_btn, LV_ALIGN_BOTTOM_MID, 0, -50);
  lv_obj_set_style_bg_color(fwup_start_btn, lv_color_hex(0x660066), 0);  // Purple
  lv_obj_set_style_bg_color(fwup_start_btn, lv_color_hex(0x880088), LV_STATE_PRESSED);
  lv_obj_set_style_radius(fwup_start_btn, 10, 0);
  lv_obj_set_style_border_width(fwup_start_btn, 0, 0);

  lv_obj_t* btn_label = lv_label_create(fwup_start_btn);
  lv_label_set_text(btn_label, "Force Update");
  lv_obj_set_style_text_color(btn_label, lv_color_white(), 0);
  lv_obj_set_style_text_font(btn_label, &cash_sans_mono_regular_20, 0);
  lv_obj_center(btn_label);

  lv_obj_clear_flag(fwup_start_btn, LV_OBJ_FLAG_PRESS_LOCK);
  lv_obj_add_event_cb(fwup_start_btn, fwup_start_btn_handler, LV_EVENT_CLICKED, NULL);

  // Reset state
  fwup_running = false;

  printf("Force FW Update screen opened\r\n");
}

// ============================================================================
// Collect Data Implementation - for FocalTech threshold tuning
// ============================================================================

// Helper: Trigger a scan and wait for completion per FocalTech doc
// Reg 0x00 bit[7]=1 starts scan, auto-clears to 0 when done
// So write 0xC0 (factory 0x40 | scan 0x80), poll until reg returns 0x40
static bool collect_scan_and_wait(void) {
  printf("  [SCAN: W 00 C0...]\r\n");
  if (!touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_SCAN_VALUE)) {
    printf("  [SCAN: Write FAILED]\r\n");
    return false;
  }
  uint32_t start = lv_tick_get();
  uint8_t val = 0;
  while ((lv_tick_get() - start) < SCAN_POLL_TIMEOUT_MS) {
    if (!touch_mfgtest_read_reg(TOUCH_MFGTEST_MODE_REG, &val)) {
      printf("  [SCAN: Poll FAILED]\r\n");
      return false;
    }
    if (val == TOUCH_MFGTEST_FACTORY_MODE) {
      printf("  [SCAN: Done in %lums]\r\n", (unsigned long)(lv_tick_get() - start));
      return true;
    }
    for (volatile int i = 0; i < 5000; i++) {
    }  // ~5ms
  }
  printf("  [SCAN: TIMEOUT reg=0x%02X]\r\n", val);
  return false;
}

// Helper: Verify we're in factory mode by reading register 0x00
// Returns true if in factory mode (0x40), false otherwise
static bool collect_verify_factory_mode(void) {
  uint8_t mode_val = 0;
  if (!touch_mfgtest_read_reg(TOUCH_MFGTEST_MODE_REG, &mode_val)) {
    printf("  [MODE CHECK: READ FAILED]\r\n");
    return false;
  }
  if (mode_val != TOUCH_MFGTEST_FACTORY_MODE) {
    printf("  [MODE CHECK: NOT IN FACTORY MODE! reg=0x%02X, expected=0x%02X]\r\n", mode_val,
           TOUCH_MFGTEST_FACTORY_MODE);
    // Try to re-enter factory mode
    printf("  [MODE CHECK: Re-entering factory mode...]\r\n");
    touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_FACTORY_MODE);
    // Verify again
    if (!touch_mfgtest_read_reg(TOUCH_MFGTEST_MODE_REG, &mode_val)) {
      printf("  [MODE CHECK: Re-read failed]\r\n");
      return false;
    }
    if (mode_val != TOUCH_MFGTEST_FACTORY_MODE) {
      printf("  [MODE CHECK: Still not in factory mode! reg=0x%02X]\r\n", mode_val);
      return false;
    }
    printf("  [MODE CHECK: Re-entry successful]\r\n");
  }
  return true;
}

// Output one section at a time via timer callback
// KITCHEN SINK approach:
// 1. V2 I2C suspend (fully release bus pins)
// 2. Double-enter factory mode with delay
// 3. Verify factory mode before each raw data read
// 4. 1000ms between each step
static void collect_output_timer_cb(lv_timer_t* timer) {
  (void)timer;
  uint8_t collect_buf[128];  // Stack buffer for I2C reads

  if (td_current_mode != MODE_COLLECT_DATA) {
    // Mode changed, cleanup
    if (collect_output_timer != NULL) {
      lv_timer_del(collect_output_timer);
      collect_output_timer = NULL;
    }
    if (collect_in_factory_mode) {
      touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_WORK_MODE);
      collect_in_factory_mode = false;
    }
    // Re-enable host I2C (V1)
    touch_set_host_i2c_suspended(false);
    return;
  }

  switch (collect_output_step) {
    case 0:
      // Suspend host I2C polling with V1 (flag only - we still need the bus!)
      touch_set_host_i2c_suspended(true);
      printf("Host I2C suspended (V1) for collection\r\n");
      // Header
      printf("\r\n");
      printf("========================================\r\n");
      printf("=== FocalTech Data Collection ===\r\n");
      printf("=== KITCHEN SINK MODE ===\r\n");
      printf("========================================\r\n");
      printf("FW Version: 0x%02X\r\n", td_touch_fw_version);
      printf("\r\n");
      break;

    case 1:
      // Enter factory mode - FIRST WRITE
      printf("Entering factory mode (double-write)...\r\n");
      printf("  Write 1: 0x%02X -> reg 0x%02X\r\n", TOUCH_MFGTEST_FACTORY_MODE,
             TOUCH_MFGTEST_MODE_REG);
      if (!touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_FACTORY_MODE)) {
        printf("  Write 1 FAILED!\r\n");
        collect_state = COLLECT_STATE_IDLE;
        if (collect_status_label != NULL) {
          lv_label_set_text(collect_status_label, "FAILED!");
          lv_obj_set_style_text_color(collect_status_label, lv_color_hex(0xFF0000), 0);
        }
        if (collect_output_timer != NULL) {
          lv_timer_del(collect_output_timer);
          collect_output_timer = NULL;
        }
        touch_set_host_i2c_suspended(false);
        return;
      }
      printf("  Write 1 OK, waiting for next step...\r\n");
      break;

    case 2:
      // Enter factory mode - SECOND WRITE (after 1000ms delay)
      printf("  Write 2: 0x%02X -> reg 0x%02X\r\n", TOUCH_MFGTEST_FACTORY_MODE,
             TOUCH_MFGTEST_MODE_REG);
      if (!touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_FACTORY_MODE)) {
        printf("  Write 2 FAILED!\r\n");
      } else {
        printf("  Write 2 OK\r\n");
      }
      collect_in_factory_mode = true;
      // Verify we're actually in factory mode
      {
        uint8_t mode_check = 0;
        if (touch_mfgtest_read_reg(TOUCH_MFGTEST_MODE_REG, &mode_check)) {
          printf("  Mode register readback: 0x%02X (expected 0x%02X)\r\n", mode_check,
                 TOUCH_MFGTEST_FACTORY_MODE);
        } else {
          printf("  Mode register readback FAILED\r\n");
        }
      }
      printf("Waiting for stabilization...\r\n\r\n");
      break;

    case 3:
      // Extra delay step for factory mode stabilization
      printf("Factory mode stabilization delay...\r\n");
      break;

    case 4:
      // === MCap RawData per FocalTech doc Section 5 ===
      // W 06 00 (rawdata mode), W 01 AA (point to MC), W 00 C0 (scan),
      // poll until 0x40, then read 0x36 for Tx*Rx*2 bytes
      printf("--- MCap RawData (Doc Sec.5) ---\r\n");
      collect_verify_factory_mode();
      touch_mfgtest_write_reg(TOUCH_MFGTEST_DATASEL_REG, 0x00);  // W 06 00 = rawdata
      touch_mfgtest_write_reg(TOUCH_MFGTEST_RAWADDR_REG,
                              TOUCH_MFGTEST_RAWADDR_MC);  // W 01 AA = mutual cap
      collect_scan_and_wait();                            // W 00 C0, poll->0x40
      memset(collect_buf, 0, TOUCH_MFGTEST_MCAP_RAW_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_RAWDATA_REG, collect_buf,
                                 TOUCH_MFGTEST_MCAP_RAW_SIZE)) {
        for (int row = 0; row < 8; row++) {
          printf("TX%d:", row);
          for (int col = 0; col < 8; col++) {
            int idx = (row * 8 + col) * 2;
            int16_t val = (int16_t)((collect_buf[idx] << 8) | collect_buf[idx + 1]);
            printf(" %6d", (int)val);
          }
          printf("\r\n");
        }
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 5:
      // === Scap CB Waterproof per FocalTech doc Section 8 ===
      // W 44 01 (waterproof mode), W 00 C0 (scan), poll,
      // W 49 00 + W 45 00 (reset CB addr), read 0x4E for (Tx+Rx)*2
      printf("--- Scap CB Waterproof (Doc Sec.8) ---\r\n");
      collect_verify_factory_mode();
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_MODE_REG, 0x01);  // W 44 01 = waterproof
      collect_scan_and_wait();                                        // W 00 C0, poll->0x40
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_ADDR_H, 0x00);    // W 49 00 = reset addr high
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_ADDR_L, 0x00);    // W 45 00 = reset addr low
      memset(collect_buf, 0, TOUCH_MFGTEST_SCAP_CB_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_SCAP_CB_DATA_REG, collect_buf,
                                 TOUCH_MFGTEST_SCAP_CB_SIZE)) {
        printf("RX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[i * 2] << 8) | collect_buf[i * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\nTX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[(8 + i) * 2] << 8) | collect_buf[(8 + i) * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\n");
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 6:
      // === Scap CB Normal per FocalTech doc Section 8 ===
      // W 44 00 (normal mode), W 00 C0 (scan), poll,
      // W 49 00 + W 45 00 (reset CB addr), read 0x4E for (Tx+Rx)*2
      printf("--- Scap CB Normal (Doc Sec.8) ---\r\n");
      collect_verify_factory_mode();
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_MODE_REG, 0x00);  // W 44 00 = normal
      collect_scan_and_wait();                                        // W 00 C0, poll->0x40
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_ADDR_H, 0x00);    // W 49 00
      touch_mfgtest_write_reg(TOUCH_MFGTEST_SCAP_CB_ADDR_L, 0x00);    // W 45 00
      memset(collect_buf, 0, TOUCH_MFGTEST_SCAP_CB_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_SCAP_CB_DATA_REG, collect_buf,
                                 TOUCH_MFGTEST_SCAP_CB_SIZE)) {
        printf("RX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[i * 2] << 8) | collect_buf[i * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\nTX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[(8 + i) * 2] << 8) | collect_buf[(8 + i) * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\n");
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 7:
      // === MCap CMB per FocalTech doc Section 9 ===
      // W 24 00 + W 26 00 (clear addr), read 0x3A for Tx*Rx bytes
      printf("--- MCap CMB (Doc Sec.9) ---\r\n");
      collect_verify_factory_mode();
      touch_mfgtest_write_reg(TOUCH_MFGTEST_MCAP_CMB_CLR1, 0x00);  // W 24 00
      touch_mfgtest_write_reg(TOUCH_MFGTEST_MCAP_CMB_CLR2, 0x00);  // W 26 00
      memset(collect_buf, 0, TOUCH_MFGTEST_MCAP_CMB_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_MCAP_CMB_REG, collect_buf,
                                 TOUCH_MFGTEST_MCAP_CMB_SIZE)) {
        for (int row = 0; row < 8; row++) {
          printf("TX%d:", row);
          for (int col = 0; col < 8; col++) {
            printf(" %4u", (unsigned)collect_buf[row * 8 + col]);
          }
          printf("\r\n");
        }
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 8:
      // === Scap RawData Waterproof per FocalTech doc Section 10 ===
      // W 00 C0 (scan), poll, W 01 AC (point to scap water),
      // read 0x36 for (Tx+Rx)*2
      printf("--- Scap RawData Waterproof (Doc Sec.10) ---\r\n");
      collect_verify_factory_mode();
      collect_scan_and_wait();  // W 00 C0, poll->0x40
      touch_mfgtest_write_reg(TOUCH_MFGTEST_RAWADDR_REG,
                              TOUCH_MFGTEST_RAWADDR_SC_WATER);  // W 01 AC
      memset(collect_buf, 0, TOUCH_MFGTEST_SCAP_RAW_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_RAWDATA_REG, collect_buf,
                                 TOUCH_MFGTEST_SCAP_RAW_SIZE)) {
        printf("RX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[i * 2] << 8) | collect_buf[i * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\nTX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[(8 + i) * 2] << 8) | collect_buf[(8 + i) * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\n");
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 9:
      // === Scap RawData Normal per FocalTech doc Section 10 ===
      // W 01 AB (point to scap normal), read 0x36 for (Tx+Rx)*2
      // Note: scan already done in case 8, data should still be valid
      printf("--- Scap RawData Normal (Doc Sec.10) ---\r\n");
      collect_verify_factory_mode();
      touch_mfgtest_write_reg(TOUCH_MFGTEST_RAWADDR_REG,
                              TOUCH_MFGTEST_RAWADDR_SC_NORMAL);  // W 01 AB
      memset(collect_buf, 0, TOUCH_MFGTEST_SCAP_RAW_SIZE);
      if (touch_mfgtest_read_buf(TOUCH_MFGTEST_RAWDATA_REG, collect_buf,
                                 TOUCH_MFGTEST_SCAP_RAW_SIZE)) {
        printf("RX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[i * 2] << 8) | collect_buf[i * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\nTX:");
        for (int i = 0; i < 8; i++) {
          int16_t val = (int16_t)((collect_buf[(8 + i) * 2] << 8) | collect_buf[(8 + i) * 2 + 1]);
          printf(" %6d", (int)val);
        }
        printf("\r\n");
      } else {
        printf("READ FAILED\r\n");
      }
      printf("\r\n");
      break;

    case 10:
      // Return to work mode and footer
      printf("Returning to work mode...\r\n");
      touch_mfgtest_write_reg(TOUCH_MFGTEST_MODE_REG, TOUCH_MFGTEST_WORK_MODE);
      collect_in_factory_mode = false;
      // Re-enable host I2C polling (V1)
      touch_set_host_i2c_suspended(false);
      printf("Host I2C resumed\r\n");
      printf("OK\r\n\r\n");
      printf("========================================\r\n");
      printf("=== End of Collection ===\r\n");
      printf("========================================\r\n");

      // Done - stop timer and update UI
      collect_state = COLLECT_STATE_DONE;
      if (collect_status_label != NULL) {
        lv_label_set_text(collect_status_label, "DONE!");
        lv_obj_set_style_text_color(collect_status_label, lv_color_hex(0x00FF00), 0);
      }
      if (collect_output_timer != NULL) {
        lv_timer_del(collect_output_timer);
        collect_output_timer = NULL;
      }
      return;

    default:
      // Should not reach here
      if (collect_output_timer != NULL) {
        lv_timer_del(collect_output_timer);
        collect_output_timer = NULL;
      }
      return;
  }

  // Move to next step
  collect_output_step++;
}

// Timer callback for collect data countdown
static void collect_countdown_timer_cb(lv_timer_t* timer) {
  (void)timer;

  if (td_current_mode != MODE_COLLECT_DATA) {
    if (collect_countdown_timer != NULL) {
      lv_timer_del(collect_countdown_timer);
      collect_countdown_timer = NULL;
    }
    return;
  }

  collect_countdown_value--;

  if (collect_countdown_value > 0) {
    // Update countdown display
    char buf[8];
    snprintf(buf, sizeof(buf), "%d...", collect_countdown_value);
    if (collect_status_label != NULL) {
      lv_label_set_text(collect_status_label, buf);
    }
    printf("Collect Data: %d...\r\n", collect_countdown_value);
  } else {
    // Countdown complete - stop timer and start output timer
    if (collect_countdown_timer != NULL) {
      lv_timer_del(collect_countdown_timer);
      collect_countdown_timer = NULL;
    }

    collect_state = COLLECT_STATE_COLLECTING;

    // Update UI to show collecting
    if (collect_status_label != NULL) {
      lv_label_set_text(collect_status_label, "Collecting...");
      lv_obj_set_style_text_color(collect_status_label, lv_color_hex(0xFFFF00), 0);
    }

    // Start output timer - outputs one section every 1000ms
    // This gives UART time to transmit and touch IC time to stabilize between reads
    collect_output_step = 0;
    collect_in_factory_mode = false;
    collect_output_timer = lv_timer_create(collect_output_timer_cb, 1000, NULL);
  }
}

// Button handler for "Start Collection" button
static void collect_start_btn_handler(lv_event_t* e) {
  lv_event_code_t code = lv_event_get_code(e);
  if (code != LV_EVENT_CLICKED) {
    return;
  }

  if (collect_state != COLLECT_STATE_IDLE && collect_state != COLLECT_STATE_DONE) {
    return;  // Already running
  }

  if (collect_countdown_timer != NULL) {
    return;  // Countdown already in progress
  }

  // Start 3-second countdown
  collect_state = COLLECT_STATE_COUNTDOWN;
  collect_countdown_value = 3;

  // Show initial countdown
  if (collect_status_label != NULL) {
    lv_label_set_text(collect_status_label, "3...");
    lv_obj_set_style_text_color(collect_status_label, lv_color_hex(0xFFFF00), 0);
  }

  printf("Collect Data: Starting 3 second countdown...\r\n");
  printf("Collect Data: 3...\r\n");

  // Start countdown timer (fires every 1 second)
  collect_countdown_timer = lv_timer_create(collect_countdown_timer_cb, 1000, NULL);
}

// Show the Collect Data screen
void td_show_collect_data(void) {
  td_clear_screen_content();
  td_current_mode = MODE_COLLECT_DATA;
  collect_state = COLLECT_STATE_IDLE;

  // Dark background
  lv_obj_set_style_bg_color(td_screen, lv_color_hex(0x1a1a1a), 0);

  // Title
  td_title_label = lv_label_create(td_screen);
  lv_label_set_text(td_title_label, "Collect Data");
  lv_obj_set_style_text_color(td_title_label, lv_color_hex(0x00FFFF), 0);  // Cyan
  lv_obj_set_style_text_font(td_title_label, &cash_sans_mono_regular_24, 0);
  lv_obj_align(td_title_label, LV_ALIGN_TOP_MID, 0, 45);

  // Description
  lv_obj_t* desc_label = lv_label_create(td_screen);
  lv_label_set_text(desc_label,
                    "Collects touch data\nfor FocalTech INI tuning\n\nImmediate UART output");
  lv_obj_set_style_text_color(desc_label, lv_color_hex(0xAAAAAA), 0);
  lv_obj_set_style_text_font(desc_label, &cash_sans_mono_regular_20, 0);
  lv_obj_set_style_text_align(desc_label, LV_TEXT_ALIGN_CENTER, 0);
  lv_obj_align(desc_label, LV_ALIGN_TOP_MID, 0, 78);

  // Status label (large, centered)
  collect_status_label = lv_label_create(td_screen);
  lv_label_set_text(collect_status_label, "Tap anywhere to start");
  lv_obj_set_style_text_color(collect_status_label, lv_color_hex(0x888888), 0);
  lv_obj_set_style_text_font(collect_status_label, &cash_sans_mono_regular_20, 0);
  lv_obj_align(collect_status_label, LV_ALIGN_CENTER, 0, 20);

  // Make whole screen clickable to start collection (tap anywhere)
  lv_obj_add_flag(td_screen, LV_OBJ_FLAG_CLICKABLE);
  lv_obj_add_event_cb(td_screen, collect_start_btn_handler, LV_EVENT_CLICKED, NULL);

  printf("Collect Data screen opened\r\n");
  printf("  Collects: MCap RawData, Scap CB (WP+Norm), MCap CMB, Scap RawData (WP+Norm)\r\n");
}

// ============================================================================
// Display Test (integrated from screen_display_test.c)
// ============================================================================

void td_diag_cleanup_mode(void) {
  if (disp_test_current == DISP_TEST_STARFIELD) {
    mfg_starfield_fps_destroy(&disp_test_starfield);
  }
}

static void disp_test_add_labels(lv_color_t text_color) {
  // Mode indicator at top
  lv_obj_t* label = lv_label_create(td_screen);
  if (label) {
    char text[32];
    snprintf(text, sizeof(text), "%d/%d: %s", disp_test_current + 1, DISP_TEST_COUNT,
             disp_test_labels[disp_test_current]);
    lv_label_set_text(label, text);
    lv_obj_set_style_text_color(label, text_color, 0);
    lv_obj_set_style_text_font(label, &cash_sans_mono_regular_20, 0);
    lv_obj_align(label, LV_ALIGN_TOP_MID, 0, 40);
  }

  // Hint at bottom
  lv_obj_t* hint = lv_label_create(td_screen);
  if (hint) {
    lv_label_set_text(hint, "< swipe >");
    lv_obj_set_style_text_color(hint, text_color, 0);
    lv_obj_set_style_text_font(hint, &cash_sans_mono_regular_20, 0);
    lv_obj_set_style_text_opa(hint, LV_OPA_50, 0);
    lv_obj_align(hint, LV_ALIGN_BOTTOM_MID, 0, -40);
  }
}

static void disp_test_setup_mode(void) {
  // Clean children but keep screen
  lv_obj_clean(td_screen);

  lv_color_t text_color;

  switch (disp_test_current) {
    case DISP_TEST_RED:
    case DISP_TEST_GREEN:
    case DISP_TEST_BLUE:
    case DISP_TEST_WHITE:
    case DISP_TEST_BLACK:
      lv_obj_set_style_bg_color(td_screen, lv_color_hex(disp_test_colors[disp_test_current]), 0);
      text_color = (disp_test_current == DISP_TEST_BLACK || disp_test_current == DISP_TEST_BLUE)
                     ? lv_color_white()
                     : lv_color_black();
      disp_test_add_labels(text_color);
      break;

    case DISP_TEST_BURNIN_GRID:
      lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
      mfg_burnin_grid_create(td_screen);
      disp_test_add_labels(lv_color_black());
      break;

    case DISP_TEST_STARFIELD:
      lv_obj_set_style_bg_color(td_screen, lv_color_black(), 0);
      mfg_starfield_fps_create(td_screen, &disp_test_starfield);
      disp_test_add_labels(lv_color_white());
      break;

    default:
      break;
  }
}

static void disp_test_gesture_handler(lv_event_t* e) {
  if (lv_event_get_code(e) != LV_EVENT_GESTURE) {
    return;
  }

  lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_get_act());

  if (dir == LV_DIR_LEFT) {
    td_diag_cleanup_mode();
    disp_test_current = (disp_test_mode_t)((disp_test_current + 1) % DISP_TEST_COUNT);
    disp_test_setup_mode();
  } else if (dir == LV_DIR_RIGHT) {
    td_diag_cleanup_mode();
    disp_test_current =
      (disp_test_mode_t)((disp_test_current + DISP_TEST_COUNT - 1) % DISP_TEST_COUNT);
    disp_test_setup_mode();
  }
}

void td_show_display_test(void) {
  td_clear_screen_content();
  td_current_mode = MODE_DISPLAY_TEST;

  // Remove all existing event callbacks on screen object (from previous modes like
  // robot test, touch viewer, etc. that add PRESSED/PRESSING/RELEASED handlers).
  // Iterate backwards to safely remove by index.
  uint32_t evt_count = lv_obj_get_event_count(td_screen);
  for (int32_t i = (int32_t)evt_count - 1; i >= 0; i--) {
    lv_obj_remove_event(td_screen, (uint32_t)i);
  }
  // Re-add the delete handler (needed for state cleanup on screen auto-delete)
  lv_obj_add_event_cb(td_screen, td_screen_delete_handler, LV_EVENT_DELETE, NULL);
  // Add gesture handler for swipe navigation between display test modes
  lv_obj_add_event_cb(td_screen, disp_test_gesture_handler, LV_EVENT_GESTURE, NULL);
  lv_obj_add_flag(td_screen, LV_OBJ_FLAG_CLICKABLE);

  // Set full brightness for display testing
  ui_set_local_brightness(100);

  disp_test_current = DISP_TEST_RED;
  disp_test_setup_mode();

  printf("Display Test opened (swipe left/right to navigate)\r\n");
}

// ============================================================================
// State reset and timer cleanup
// ============================================================================

void td_diag_reset_state(void) {
  // Force FWUP
  fwup_status_label = NULL;
  fwup_start_btn = NULL;
  fwup_progress_label = NULL;
  fwup_running = false;

  // Collect Data
  collect_status_label = NULL;
  collect_state = COLLECT_STATE_IDLE;
  collect_countdown_timer = NULL;
  collect_countdown_value = 0;
  collect_output_step = 0;
  collect_output_timer = NULL;
  collect_in_factory_mode = false;

  // Display Test
  disp_test_current = DISP_TEST_RED;
  disp_test_starfield = (mfg_starfield_fps_t){0};
}

void td_diag_cleanup_timers(void) {
  // Delete any active timers: collect_countdown_timer, collect_output_timer
  if (collect_countdown_timer != NULL) {
    lv_timer_del(collect_countdown_timer);
    collect_countdown_timer = NULL;
  }
  if (collect_output_timer != NULL) {
    lv_timer_del(collect_output_timer);
    collect_output_timer = NULL;
  }
}

#endif /* MFGTEST */
