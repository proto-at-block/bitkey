#pragma once

#include "mcu_gpio.h"
#include "mcu_qspi.h"

#include <stdbool.h>
#include <stdint.h>

typedef struct gfx_config {
  mcu_qspi_config_t* display_qspi_config;  // Pointer to QSPI config
  mcu_gpio_config_t rst;                   // Reset pin
  mcu_gpio_config_t te;                    // Tearing effect pin
  uint16_t display_width;                  // Horizontal resolution
  uint16_t display_height;                 // Vertical resolution
  bool rotate_180;                         // Rotate display 180 degrees
} gfx_config_t;

void gfx_init(const gfx_config_t* gfx_config);
void gfx_set_brightness(uint8_t level);

uint32_t gfx_get_fps(void);            // Flush rate (partial frames/sec)
uint32_t gfx_get_effective_fps(void);  // Full frame equivalent FPS

// callback declaration
typedef void (*gfx_flush_complete_cb_t)(void* user_data);
void gfx_flush(uint8_t* buffer, uint16_t x1, uint16_t y1, uint16_t x2, uint16_t y2,
               gfx_flush_complete_cb_t callback, void* user_data);
