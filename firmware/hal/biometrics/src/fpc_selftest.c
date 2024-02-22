// clang-format off
#include <stddef.h>  // FPC forgot to include stddef.h in some of their headers,
                     // so this include must come first.
// clang-format on

#include "assert.h"
#include "bio_impl.h"
#include "fpc_bep_image.h"
#include "fpc_bep_sensor.h"
#include "fpc_bep_sensor_test.h"
#include "fpc_bep_types.h"
#include "fpc_malloc.h"
#include "fpc_sensor_spi.h"
#include "fpc_timebase.h"
#include "log.h"
#include "printf.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#define BITMAP_CHUNK_SIZE 128

#define FPC1xxx_WAKE_UP                   ((uint8_t)0x22)
#define FPC1xxx_INIT_SECURITY_MODE        ((uint8_t)0x26)
#define FPC102x_READ_INTERRUPT_WITH_CLEAR ((uint8_t)0x1C)
#define FPC102x_REG_IMAGE_RD              ((uint8_t)0x64)
#define FPC102x_REG_HWID                  ((uint8_t)0xFC)
#define FPC102x_SOFT_RESET                ((uint8_t)0xF8)
#define FPC102x_REG_STATUS                ((uint8_t)0x14)
#define FPC102x_CAPTURE_IMAGE             ((uint8_t)0xC0)
#define FPC102x_READ_IMAGE_DATA           ((uint8_t)0xC4)

#define FPC102x_IRQ_FINGER_DOWN (1u << 0)
#define FPC102x_IRQ_ERROR       (1u << 2)

typedef bool (*sensor_init_t)(void);
typedef bool (*sensor_capture_init_t)(void);
typedef bool (*sensor_reg_test_t)(uint8_t val, uint8_t* ret_val);

static bool sensor_init_fpc1323(void);
static bool sensor_capture_init_fpc1323(void);
static bool sensor_reg_test_fpc1323(uint8_t val, uint8_t* ret_val);

#define TEST_IMAGE_SIZE  (96 * 96)
#define TEST_BITMAP_SIZE (1152)

static struct {
  /** Sensor name */
  const char* name;
  /** ASIC HWID */
  uint16_t asic_id;
  /** ASIC HWID mask. Only some bits are used. */
  uint16_t asic_id_mask;
  /** Horizontal number of pixels */
  uint16_t width;
  /** Vertical number of pixels */
  uint16_t height;
  /** Max SPI speed */
  uint32_t spi_freq;
  /** Hard or soft reset */
  bool soft_reset;
  /** Use irq polling */
  bool irq_polling;
  /** Pixel value threshold for stress test */
  uint8_t pixel_threshold;
  /** Init function */
  sensor_init_t init;
  /** Capture init function */
  sensor_capture_init_t capture_init;
  /** Test register function (write to reg and verify written value) */
  sensor_reg_test_t reg_test;
} sensor_cfg = {
  .name = "FPC1323",
  .asic_id = 0x1700,
  .asic_id_mask = 0xff00,
  .width = 96,
  .height = 96,
  .spi_freq = 3000000,
  .soft_reset = false,
  .irq_polling = false,
  .pixel_threshold = 0xA0,
  .init = sensor_init_fpc1323,
  .capture_init = sensor_capture_init_fpc1323,
  .reg_test = sensor_reg_test_fpc1323,
};

static void soft_reset(void) {
  uint8_t command[1] = {FPC102x_SOFT_RESET};
  (void)fpc_sensor_spi_write_read(command, 1, 0, false);
  fpc_timebase_delay_ms(1);
}

static void hard_reset(void) {
  fpc_sensor_spi_reset(true);
  fpc_timebase_delay_ms(1);
  fpc_sensor_spi_reset(false);
  fpc_timebase_delay_ms(1);
}

static bool bepit_sensor_reg_write(uint8_t reg, const uint8_t* data, size_t sz) {
  uint8_t buffer[1 + sz];
  fpc_bep_result_t res;

  buffer[0] = reg;

  if (sz > 0 && data != NULL) {
    memcpy(&(buffer[1]), data, sz);
  }

  res = fpc_sensor_spi_write_read(buffer, 1 + sz, 0, false);

  return (res == FPC_BEP_RESULT_OK);
}

static bool bepit_sensor_reg_read(uint8_t reg, uint8_t* data, size_t sz) {
  uint8_t buffer[1 + sz];
  fpc_bep_result_t res;

  buffer[0] = reg;

  res = fpc_sensor_spi_write_read(buffer, 1, sz, false);

  if (res == FPC_BEP_RESULT_OK) {
    if (sz > 0 && data != NULL) {
      memcpy(data, &(buffer[1]), sz);
    }
  }

  return (res == FPC_BEP_RESULT_OK);
}

static bool sensor_read_irq(void) {
  if (sensor_cfg.irq_polling) {
    uint8_t reg[2] = {0};
    const uint8_t status_reg_bit_irq = 0x01;

    (void)bepit_sensor_reg_read(FPC102x_REG_STATUS, reg, 2);
    fpc_timebase_delay_ms(1);

    return (bool)(reg[1] & status_reg_bit_irq);
  } else {
    return fpc_sensor_spi_read_irq();
  }
}

static bool sensor_irq_clear(void) {
  fpc_bep_result_t result;
  uint8_t command[2] = {FPC102x_READ_INTERRUPT_WITH_CLEAR, 0};
  bool irq_active = false;

  /* Clear sensor interrupt */
  result = fpc_sensor_spi_write_read(command, 1, 1, false);
  if (result != FPC_BEP_RESULT_OK) {
    return false;
  }
  fpc_timebase_delay_ms(1);

  /* Interrupt flag should be inactive */
  irq_active = sensor_read_irq();
  return !irq_active;
}

static bool bepit_sensor_irq_test(void) {
  bool irq_inactive_test = false;
  bool irq_active_test = false;

  if (sensor_cfg.soft_reset) {
    soft_reset();
  } else {
    hard_reset();
  }

  if (sensor_cfg.init) {
    sensor_cfg.init();
  }

  // Interrupt flag should be active
  irq_active_test = sensor_read_irq();
  irq_inactive_test = sensor_irq_clear();

  return (irq_inactive_test && irq_active_test);
}

static bool bepit_sensor_spi_rw_test(void) {
  bool res = true;
  uint8_t reg[2] = {0};
  // Read hardware version
  res = bepit_sensor_reg_read(FPC102x_REG_HWID, reg, 2);
  if (res) {
    if ((reg[0] == 0xff && reg[1] == 0xff) || (reg[0] == 0 && reg[1] == 0)) {
      res = false;
    }
  }
  if (res) {
    uint16_t sensor_hwid = (reg[0] << 8) | (reg[1] << 0);
    (void)sensor_hwid;
  }
  return res;
}

static bool sensor_capture(uint8_t* image, size_t sz, uint32_t* capture_time) {
  bool res = true;
  fpc_bep_result_t result = FPC_BEP_RESULT_OK;

  /* Reset sensor */
  bepit_sensor_irq_test();

  /* Do sensor specific initialization for capture */
  res = sensor_cfg.capture_init();

  /* Send capture command */
  if (res) {
    res = bepit_sensor_reg_write(FPC102x_CAPTURE_IMAGE, NULL, 0);
  }

  if (res) {
    /* Wait for interrupt */
    while (!sensor_read_irq())
      ;
    sensor_irq_clear();
  }

  /* Read captured sensor data */
  if (res) {
    uint8_t buffer[2] = {FPC102x_READ_IMAGE_DATA, 0};

    result = fpc_sensor_spi_write_read(buffer, 1, 1, true);
    res = (result == FPC_BEP_RESULT_OK);
  }

  if (res) {
    uint32_t start;
    memset(image, 0, sz);
    start = fpc_timebase_get_tick();
    result = fpc_sensor_spi_write_read(image, 0, sz, false);
    res = (result == FPC_BEP_RESULT_OK);
    if (capture_time != NULL) {
      *capture_time = fpc_timebase_get_tick() - start;
    }
  }

  toggle_cs(false);  // Off

  /* Check if an error occurred */
  if (res) {
    if (sensor_read_irq()) {
      uint8_t buffer[2] = {(uint8_t)FPC102x_READ_INTERRUPT_WITH_CLEAR, 0};
      /* Read the data in the interrupt register */
      (void)fpc_sensor_spi_write_read(buffer, 1, 1, false);
      if (buffer[1] & FPC102x_IRQ_ERROR) {
        res = false;
      }
    }
  }

  return res;
}

static bool bepit_sensor_spi_reg_stress_test(int param) {
  /* Read/Write Finger drive register to test SPI communication */
  bool res = true;
  uint8_t val = 0xaa;
  uint8_t ret_val = 0;

  while (param--) {
    res = sensor_cfg.reg_test(val, &ret_val);
    if (!res) {
      res = false;
      break;
    }

    if (ret_val != val) {
      res = false;
      break;
    }
    val = ~val;
  }

  return res;
}

static void generate_testbitmap(uint8_t* image, uint8_t* bitmap, int sz) {
  int i;
  uint8_t threshold = sensor_cfg.pixel_threshold;

  memset(bitmap, 0, sz / 8);
  for (i = 0; i < sz; i++) {
    *bitmap = *bitmap << 1;
    if (*image > threshold) {
      *bitmap |= 1;
    }
    if ((i % 8) == 7) {
      bitmap++;
    }
    image++;
  }
}

static bool bepit_sensor_spi_image_stress_test(int param) {
  /* Read image to test larger SPI transactions */
  bool res = true;
  uint8_t* testbitmap = fpc_malloc(TEST_IMAGE_SIZE + 1);
  uint32_t image_size = sensor_cfg.width * sensor_cfg.height;
  uint8_t* image = fpc_malloc(image_size);

  /* Make initial testbitmap */
  res = sensor_capture(image, image_size, NULL);
  if (res) {
    uint32_t pos;
    for (pos = 0; pos < image_size; pos += BITMAP_CHUNK_SIZE) {
      generate_testbitmap(&image[pos], &testbitmap[pos / 8], BITMAP_CHUNK_SIZE);
    }
  }

  while (param-- && res) {
    uint8_t bitmapchunk[BITMAP_CHUNK_SIZE / 8];

    if (res) {
      res = sensor_capture(image, image_size, NULL);
    }
    if (res) {
      uint32_t pos;
      for (pos = 0; pos < image_size; pos += BITMAP_CHUNK_SIZE) {
        generate_testbitmap(&image[pos], bitmapchunk, BITMAP_CHUNK_SIZE);
        if (memcmp(bitmapchunk, &testbitmap[pos / 8], BITMAP_CHUNK_SIZE / 8)) {
          res = false;
          break;
        }
      }
    }
  }

  fpc_free(image);
  fpc_free(testbitmap);

  return res;
}

bool bepit_sensor_spi_speed_test(int param) {
  /* Read image to test larger SPI transactions */
  bool res = true;
  uint32_t capture_time = 0;
  uint32_t calculated_spi_bitrate;
  uint32_t image_size = sensor_cfg.width * sensor_cfg.height;
  uint8_t* image = fpc_malloc(image_size);
  int i;

  if (param <= 0) {
    return false;
  }

  for (i = 0; i < param; i++) {
    uint32_t time;
    res = sensor_capture(image, image_size, &time);
    if (!res) {
      break;
    }
    capture_time += time;
  }
  calculated_spi_bitrate = 1000 * image_size / (capture_time / param) * 8;
  (void)calculated_spi_bitrate;

  fpc_free(image);

  return res;
}

bool sensor_init_fpc1323(void) {
  bool res = true;

  res = bepit_sensor_reg_write(FPC1xxx_WAKE_UP, NULL, 0);
  fpc_timebase_delay_ms(1);

  return res;
}

static bool sensor_capture_init_fpc1323(void) {
  bool res = true;
  // Settings come from FPC example code.
  static const uint8_t settings[] = {
    1,    0x46, 0x02, 4,    0x8C, 0x00, 0x04, 0x18, 0x64, 17,   0x6C, 0x0E, 0x0A, 0x08, 0x00,
    0x00, 0x09, 0x05, 0x03, 0x0E, 0x0E, 0x08, 0x0D, 0x0B, 0x07, 0x05, 0x01, 0x00, 6,    0x66,
    0x04, 0x14, 0x04, 0x00, 0x01, 0x48, 2,    0x9C, 0x30, 0xEB, 3,    0x64, 0x0A, 0x02, 0x01,
    1,    0x6E, 0x0C, 1,    0x5C, 0x00, 10,   0xA8, 0x01, 0x00, 0x00, 0x55, 0x55, 0x55, 0x55,
    0x23, 0x30, 0xED, 3,    0x70, 0x01, 0x7E, 0x30, 6,    0x54, 0x00, 0x00, 0x00, 0x60, 0x00,
    0x60, 8,    0x88, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 1,    0x78, 0x21, 0};
  int i = 0;

  /*  Write settings */
  while (res && (settings[i] != 0)) {
    res = bepit_sensor_reg_write(settings[i + 1], &settings[i + 2], settings[i]);
    i += settings[i] + 2;
  }

  if (res) {
    res = bepit_sensor_reg_write(FPC1xxx_INIT_SECURITY_MODE, NULL, 0);
    fpc_timebase_delay_ms(1);
  }

  return res;
}

static bool sensor_reg_test_fpc1323(uint8_t val, uint8_t* ret_val) {
  /* Read/Write first byte of ImageRead register */
  bool res = true;
  uint8_t reg[3] = {val, 0, 0};

  res = bepit_sensor_reg_write(FPC102x_REG_IMAGE_RD, reg, 3);

  if (res) {
    res = bepit_sensor_reg_read(FPC102x_REG_IMAGE_RD, reg, 3);
  }

  if (res) {
    *ret_val = reg[0];
  }

  return res;
}

bool bio_image_capture_test(uint8_t** image_out, uint32_t* image_size_out) {
  fpc_bep_image_t* image = fpc_bep_image_new();
  if (!bio_capture_image(image, 5)) {
    goto fail;
  }

  uint8_t* pixels = fpc_bep_image_get_pixels(image);
  *image_size_out = (uint32_t)fpc_bep_image_get_size(image);
  *image_out = fpc_malloc(*image_size_out);
  memcpy(*image_out, pixels, *image_size_out);
  fpc_bep_image_delete(&image);

  return true;

fail:
  fpc_bep_image_delete(&image);
  return false;
}

void bio_selftest(bio_selftest_result_t* result) {
  toggle_cs(false);

  LOGI("bepit_sensor_irq_test...");
  result->irq_test = bepit_sensor_irq_test();
  LOGI("bepit_sensor_spi_rw_test...");
  result->spi_rw_test = bepit_sensor_spi_rw_test();
  LOGI("bepit_sensor_spi_speed_test...");
  result->spi_speed_test = bepit_sensor_spi_speed_test(100);
  LOGI("bepit_sensor_spi_image_stress_test...");
  result->image_stress_test = bepit_sensor_spi_image_stress_test(10);
  LOGI("bepit_sensor_spi_reg_stress_test...");
  result->reg_stress_test = bepit_sensor_spi_reg_stress_test(1000);

  LOGI("fpc_bep_sensor_otp_test...");
  result->otp_test = fpc_bep_sensor_otp_test() == FPC_BEP_RESULT_OK;
  LOGI("fpc_bep_sensor_prod_test...");
  result->prod_test = fpc_bep_sensor_prod_test() == FPC_BEP_RESULT_OK;
}

bool bio_quick_selftest(void) {
  toggle_cs(false);
  return bepit_sensor_irq_test() && bepit_sensor_spi_rw_test();
}
