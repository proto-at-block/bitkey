/**
 * @file touch_fwup.c
 *
 * @brief FT3169 touch controller firmware upgrade implementation.
 *
 * This module implements firmware upgrade functionality for the FocalTech
 * FT3169 touch controller.
 */

#include "log.h"
#include "rtos.h"
#include "touch.h"
#include "touch_ft3169.h"
#include "touch_internal.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

// Firmware upgrade registers and commands
#define FWUP_REG_UPGRADE    0xFC
#define FWUP_CMD_RESET      0x07
#define FWUP_CMD_FLASH_MODE 0x09
#define FWUP_CMD_ERASE_APP  0x61
#define FWUP_CMD_ECC_INIT   0x64
#define FWUP_CMD_ECC_CAL    0x65
#define FWUP_CMD_ECC_READ   0x66
#define FWUP_CMD_STATUS     0x6A
#define FWUP_CMD_DATA_LEN   0x7A
#define FWUP_CMD_WRITE      0xBF

// Flash status codes
#define FWUP_STATUS_ERASE_OK 0xF0AA
#define FWUP_STATUS_ECC_OK   0xF055

// Upgrade sequence bytes
#define FWUP_BYTE_AA 0xAA
#define FWUP_BYTE_55 0x55

// Flash mode value
#define FWUP_FLASH_MODE_UPGRADE 0x0B

// Timing parameters (milliseconds)
#define FWUP_DELAY_AA_WRITE      10
#define FWUP_DELAY_BOOT_MODE     80
#define FWUP_DELAY_READ_ID       20
#define FWUP_DELAY_AFTER_RESET   400
#define FWUP_DELAY_PACKET_WRITE  1
#define FWUP_DELAY_ERASE_POLL    400
#define FWUP_DELAY_ECC_POLL      50
#define FWUP_DELAY_FW_VALID_READ 100

// Retry counts
#define FWUP_RETRIES_UPGRADE  2
#define FWUP_RETRIES_CHECK_ID 20
#define FWUP_RETRIES_ERASE    50
#define FWUP_RETRIES_ECC      10
#define FWUP_RETRIES_WRITE    100
#define FWUP_RETRIES_FW_VALID 5

// Firmware size limits
#define FWUP_FW_MIN_SIZE       0x120
#define FWUP_FW_MAX_SIZE       (64 * 1024)
#define FWUP_FW_VERSION_OFFSET 0x010E
#define FWUP_FLASH_PACKET_SIZE 128

// ECC calculation parameters
#define FWUP_ECC_CMD_LEN 6

// Firmware upgrade request state
typedef enum {
  FWUP_REQUEST_NONE = 0,
  FWUP_REQUEST_NORMAL,
  FWUP_REQUEST_FORCE,
} fwup_request_t;

static bool priv_fw_valid SHARED_TASK_BSS = false;
static volatile fwup_request_t priv_pending_request SHARED_TASK_DATA = FWUP_REQUEST_NONE;

// Embedded firmware image
static const uint8_t priv_fw_image[] = {
#include "ft3169_fw.i"
};

// Forward declarations
static bool _touch_fwup_check_flash_status(uint16_t expected, int retries, int delay_ms);
static bool _touch_fwup_enter_boot_mode(void);
static bool _touch_fwup_erase_flash(uint32_t fw_size);
static bool _touch_fwup_write_flash(uint32_t addr, const uint8_t* data, uint32_t len);
static int _touch_fwup_calc_ecc_host(const uint8_t* data, uint32_t len);
static int _touch_fwup_calc_ecc_device(uint32_t addr, uint32_t len);
static bool _touch_fwup_execute(const uint8_t* fw_data, uint32_t fw_size, uint16_t* ecc_host_out,
                                uint16_t* ecc_device_out);
static bool _touch_fwup_check_fw_valid(void);
static bool _touch_fwup_version_differs(const uint8_t* fw_data, uint32_t fw_size);

/**
 * @brief Poll flash status register until expected value or timeout.
 */
static bool _touch_fwup_check_flash_status(uint16_t expected, int retries, int delay_ms) {
  uint8_t buf[2] = {0};

  for (int i = 0; i < retries; i++) {
    if (touch_i2c_read(FWUP_CMD_STATUS, buf, sizeof(buf))) {
      uint16_t status = ((uint16_t)buf[0] << 8) | buf[1];
      if (status == expected) {
        return true;
      }
    }
    rtos_thread_sleep(delay_ms);
  }

  return false;
}

/**
 * @brief Verify chip ID matches expected boot mode ID.
 */
static bool _touch_fwup_verify_boot_id(void) {
  uint8_t buf[2] = {0};

  // Initial ID read sequence
  touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_AA);
  touch_i2c_read(0xC0, buf, sizeof(buf));

  touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_55);
  touch_i2c_read(0xC0, buf, sizeof(buf));

  rtos_thread_sleep(FWUP_DELAY_BOOT_MODE);

  // Confirm we're in boot mode by reading chip ID
  for (int i = 0; i < FWUP_RETRIES_CHECK_ID; i++) {
    touch_write_reg(FWUP_BYTE_55, FWUP_BYTE_AA);
    rtos_thread_sleep(FWUP_DELAY_AA_WRITE);

    if (touch_i2c_read(FT3169_CMD_READ_ID, buf, sizeof(buf))) {
      if (buf[0] == FT3169_CHIP_IDH && buf[1] == FT3169_CHIP_IDL) {
        LOGI("Boot ID: %02x%02x", buf[0], buf[1]);
        return true;
      }
    }

    touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_AA);
    touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_55);
    rtos_thread_sleep(FWUP_DELAY_READ_ID);
  }

  LOGE("Boot ID fail");
  return false;
}

/**
 * @brief Enter boot mode for firmware upgrade.
 */
static bool _touch_fwup_enter_boot_mode(void) {
  if (priv_fw_valid) {
    // Software reset to boot mode
    if (!touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_AA)) {
      LOGE("Reg AA fail");
      return false;
    }
    rtos_thread_sleep(FWUP_DELAY_AA_WRITE);

    if (!touch_write_reg(FWUP_REG_UPGRADE, FWUP_BYTE_55)) {
      LOGE("Reg 55 fail");
      return false;
    }
    rtos_thread_sleep(FWUP_DELAY_BOOT_MODE);
  }

  return _touch_fwup_verify_boot_id();
}

/**
 * @brief Erase flash area.
 */
static bool _touch_fwup_erase_flash(uint32_t fw_size) {
  if (!touch_i2c_write_cmd(FWUP_CMD_ERASE_APP)) {
    LOGE("Erase command failed");
    return false;
  }

  // Wait based on firmware size (60ms per 4KB sector)
  uint32_t erase_delay = 60 * (fw_size / 4096);
  rtos_thread_sleep(erase_delay);

  if (!_touch_fwup_check_flash_status(FWUP_STATUS_ERASE_OK, FWUP_RETRIES_ERASE,
                                      FWUP_DELAY_ERASE_POLL)) {
    LOGE("Erase sts fail");
    return false;
  }

  return true;
}

/**
 * @brief Write firmware data to flash in packets.
 */
static bool _touch_fwup_write_flash(uint32_t start_addr, const uint8_t* data, uint32_t len) {
  if (data == NULL || len == 0) {
    LOGE("Wr: bad params");
    return false;
  }

  uint32_t num_packets = (len + FWUP_FLASH_PACKET_SIZE - 1) / FWUP_FLASH_PACKET_SIZE;
  uint8_t packet[FWUP_FLASH_PACKET_SIZE + 6] = {0};

  for (uint32_t i = 0; i < num_packets; i++) {
    uint32_t offset = i * FWUP_FLASH_PACKET_SIZE;
    uint32_t addr = start_addr + offset;
    uint16_t packet_len = FWUP_FLASH_PACKET_SIZE;

    // Last packet may be smaller
    if (i == num_packets - 1 && (len % FWUP_FLASH_PACKET_SIZE) != 0) {
      packet_len = len % FWUP_FLASH_PACKET_SIZE;
    }

    // Build packet: cmd, addr(3), len(2), data
    packet[0] = FWUP_CMD_WRITE;
    packet[1] = (addr >> 16) & 0xFF;
    packet[2] = (addr >> 8) & 0xFF;
    packet[3] = addr & 0xFF;
    packet[4] = (packet_len >> 8) & 0xFF;
    packet[5] = packet_len & 0xFF;
    memcpy(&packet[6], data + offset, packet_len);

    if (!touch_i2c_write(packet[0], &packet[1], packet_len + 5)) {
      LOGE("Write failed at offset 0x%x", (unsigned)offset);
      return false;
    }
    rtos_thread_sleep(FWUP_DELAY_PACKET_WRITE);

    // Verify write status
    uint16_t expected_status = 0x1000 + (addr / FWUP_FLASH_PACKET_SIZE);
    uint8_t status_buf[2] = {0};

    for (int j = 0; j < FWUP_RETRIES_WRITE; j++) {
      if (touch_i2c_read(FWUP_CMD_STATUS, status_buf, sizeof(status_buf))) {
        uint16_t status = ((uint16_t)status_buf[0] << 8) | status_buf[1];
        if (status == expected_status) {
          break;
        }
      }
      rtos_thread_sleep(FWUP_DELAY_PACKET_WRITE);
    }
  }

  return true;
}

/**
 * @brief Calculate ECC checksum on host.
 */
static int _touch_fwup_calc_ecc_host(const uint8_t* data, uint32_t len) {
  uint16_t ecc = 0;
  const uint16_t polynomial = (1 << 15) | (1 << 10) | (1 << 3);

  for (uint32_t i = 0; i < len; i += 2) {
    ecc ^= ((uint16_t)data[i] << 8) | data[i + 1];
    for (int j = 0; j < 16; j++) {
      if (ecc & 0x01) {
        ecc = (ecc >> 1) ^ polynomial;
      } else {
        ecc >>= 1;
      }
    }
  }

  return ecc;
}

/**
 * @brief Calculate ECC checksum on device.
 */
static int _touch_fwup_calc_ecc_device(uint32_t addr, uint32_t len) {
  uint8_t cmd[7] = {0};
  uint8_t result[2] = {0};

  // Initialize ECC calculation
  if (!touch_i2c_write_cmd(FWUP_CMD_ECC_INIT)) {
    LOGE("ECC init fail");
    return -1;
  }

  // Send ECC calculation command
  cmd[0] = FWUP_CMD_ECC_CAL;
  cmd[1] = (addr >> 16) & 0xFF;
  cmd[2] = (addr >> 8) & 0xFF;
  cmd[3] = addr & 0xFF;
  cmd[4] = (len >> 16) & 0xFF;
  cmd[5] = (len >> 8) & 0xFF;
  cmd[6] = len & 0xFF;

  if (!touch_i2c_write(cmd[0], &cmd[1], FWUP_ECC_CMD_LEN)) {
    LOGE("ECC calc fail");
    return -1;
  }

  // Wait for calculation (scaled by data size)
  rtos_thread_sleep(len / 256);

  if (!_touch_fwup_check_flash_status(FWUP_STATUS_ECC_OK, FWUP_RETRIES_ECC, FWUP_DELAY_ECC_POLL)) {
    LOGE("ECC sts fail");
    return -1;
  }

  // Read ECC result
  if (!touch_i2c_read(FWUP_CMD_ECC_READ, result, sizeof(result))) {
    LOGE("ECC rd fail");
    return -1;
  }

  return ((uint16_t)result[0] << 8) | result[1];
}

/**
 * @brief Execute the firmware upgrade sequence.
 *
 * @param[in]  fw_data        Pointer to firmware data.
 * @param[in]  fw_size        Size of firmware data.
 * @param[out] ecc_host_out   Optional: store host ECC (NULL to ignore).
 * @param[out] ecc_device_out Optional: store device ECC (NULL to ignore).
 */
static bool _touch_fwup_execute(const uint8_t* fw_data, uint32_t fw_size, uint16_t* ecc_host_out,
                                uint16_t* ecc_device_out) {
  uint8_t cmd[5] = {0};

  if (ecc_host_out)
    *ecc_host_out = 0;
  if (ecc_device_out)
    *ecc_device_out = 0;

  if (fw_data == NULL || fw_size < FWUP_FW_MIN_SIZE || fw_size > FWUP_FW_MAX_SIZE) {
    LOGE("Bad FW sz: %u", (unsigned)fw_size);
    return false;
  }

  // Enter boot mode
  if (!_touch_fwup_enter_boot_mode()) {
    LOGE("Boot mode fail");
    goto err;
  }

  // Send unlock sequence
  cmd[0] = 0x02;
  cmd[1] = 0x55;
  cmd[2] = 0xAA;
  cmd[3] = 0x5A;
  cmd[4] = 0xA5;
  if (!touch_i2c_write(cmd[0], &cmd[1], 4)) {
    LOGE("Unlock fail");
    goto err;
  }

  // Send firmware size
  cmd[0] = FWUP_CMD_DATA_LEN;
  cmd[1] = (fw_size >> 16) & 0xFF;
  cmd[2] = (fw_size >> 8) & 0xFF;
  cmd[3] = fw_size & 0xFF;
  if (!touch_i2c_write(cmd[0], &cmd[1], 3)) {
    LOGE("FW size fail");
    goto err;
  }

  // Set flash mode
  if (!touch_write_reg(FWUP_CMD_FLASH_MODE, FWUP_FLASH_MODE_UPGRADE)) {
    LOGE("Flash mode fail");
    goto err;
  }

  // Erase flash
  if (!_touch_fwup_erase_flash(fw_size)) {
    goto err;
  }

  // Write firmware
  if (!_touch_fwup_write_flash(0, fw_data, fw_size)) {
    goto err;
  }

  // Verify ECC
  int ecc_host = _touch_fwup_calc_ecc_host(fw_data, fw_size);
  int ecc_device = _touch_fwup_calc_ecc_device(0, fw_size);

  if (ecc_host_out)
    *ecc_host_out = (uint16_t)ecc_host;
  if (ecc_device_out)
    *ecc_device_out = (uint16_t)ecc_device;

  LOGI("ECC h:%04x d:%04x", ecc_host, ecc_device);
  if (ecc_host != ecc_device) {
    LOGE("ECC mismatch");
    goto err;
  }

  // Reset to normal operation
  LOGI("Fwup done, rst");
  touch_i2c_write_cmd(FWUP_CMD_RESET);
  rtos_thread_sleep(FWUP_DELAY_AFTER_RESET);

  return true;

err:
  touch_i2c_write_cmd(FWUP_CMD_RESET);
  return false;
}

/**
 * @brief Check if firmware in touch controller is valid.
 */
static bool _touch_fwup_check_fw_valid(void) {
  uint8_t id = 0;

  for (int i = 0; i < FWUP_RETRIES_FW_VALID; i++) {
    if (touch_read_reg(FT3169_REG_CHIP_ID, &id)) {
      // TODO(W-15701): Remove hardcoded success once firmware is stable
      // Should check: id == FT3169_CHIP_IDH
      return true;
    }
    rtos_thread_sleep(FWUP_DELAY_FW_VALID_READ);
  }

  return false;
}

/**
 * @brief Check if firmware version differs from embedded image.
 */
static bool _touch_fwup_version_differs(const uint8_t* fw_data, uint32_t fw_size) {
  uint8_t device_ver = 0;
  uint8_t image_ver = 0;

  if (!touch_read_reg(FT3169_REG_FW_VER, &device_ver)) {
    LOGE("FW ver rd fail");
    return true;
  }

  if (fw_size > FWUP_FW_VERSION_OFFSET) {
    image_ver = fw_data[FWUP_FW_VERSION_OFFSET];
  }

  LOGI("FW ver: dev=0x%02x img=0x%02x", device_ver, image_ver);
  return device_ver != image_ver;
}

// Public API

bool touch_fwup_upgrade(void) {
  const uint8_t* fw_data = priv_fw_image;
  uint32_t fw_size = sizeof(priv_fw_image);

  if (fw_size < FWUP_FW_MIN_SIZE) {
    return false;
  }

  if (!_touch_fwup_version_differs(fw_data, fw_size)) {
    return true;
  }

  for (int attempt = 0; attempt < FWUP_RETRIES_UPGRADE; attempt++) {
    if (_touch_fwup_execute(fw_data, fw_size, NULL, NULL)) {
      uint8_t ver = 0;
      touch_read_reg(FT3169_REG_FW_VER, &ver);
      LOGI("Fwup ok v:%02x", ver);
      return true;
    }
  }

  LOGE("Fwup fail");
  return false;
}

bool touch_fwup_force_upgrade(void) {
  const uint8_t* fw_data = priv_fw_image;
  uint32_t fw_size = sizeof(priv_fw_image);

  if (fw_size < FWUP_FW_MIN_SIZE) {
    return false;
  }

  priv_fw_valid = _touch_fwup_check_fw_valid();

  for (int attempt = 0; attempt < FWUP_RETRIES_UPGRADE; attempt++) {
    if (_touch_fwup_execute(fw_data, fw_size, NULL, NULL)) {
      uint8_t ver = 0;
      touch_read_reg(FT3169_REG_FW_VER, &ver);
      LOGI("Fwup ok v:%02x", ver);
      return true;
    }
  }

  LOGE("Fwup fail");
  return false;
}

#ifdef MFGTEST
bool touch_fwup_force_upgrade_with_ecc(touch_fwup_result_t* result) {
  const uint8_t* fw_data = priv_fw_image;
  uint32_t fw_size = sizeof(priv_fw_image);

  if (result) {
    result->success = false;
    result->ecc_host = 0;
    result->ecc_device = 0;
    result->fw_version = 0;
  }

  LOGI("Touch firmware force upgrade (with ECC reporting)");

  if (fw_size < FWUP_FW_MIN_SIZE) {
    return false;
  }

  priv_fw_valid = _touch_fwup_check_fw_valid();

  for (int attempt = 0; attempt < FWUP_RETRIES_UPGRADE; attempt++) {
    LOGI("Force upgrade attempt %d", attempt + 1);

    uint16_t ecc_host = 0;
    uint16_t ecc_device = 0;

    if (_touch_fwup_execute(fw_data, fw_size, &ecc_host, &ecc_device)) {
      rtos_thread_sleep(200);

      uint8_t ver = 0;
      touch_read_reg(FT3169_REG_FW_VER, &ver);
      LOGI("Force upgrade OK, ver=0x%02x, ECC h:0x%04x d:0x%04x", ver, ecc_host, ecc_device);

      if (result) {
        result->success = true;
        result->ecc_host = ecc_host;
        result->ecc_device = ecc_device;
        result->fw_version = ver;
      }
      return true;
    }

    if (result) {
      result->ecc_host = ecc_host;
      result->ecc_device = ecc_device;
    }
  }

  LOGE("Force upgrade failed after %d attempts", FWUP_RETRIES_UPGRADE);
  return false;
}
#endif /* MFGTEST */

uint8_t touch_fwup_get_version(void) {
  uint8_t ver = 0;
  touch_read_reg(FT3169_REG_FW_VER, &ver);
  return ver;
}

uint8_t touch_fwup_get_embedded_version(void) {
  if (sizeof(priv_fw_image) > FWUP_FW_VERSION_OFFSET) {
    return priv_fw_image[FWUP_FW_VERSION_OFFSET];
  }
  return 0;
}

void touch_fwup_request_upgrade(bool force) {
  priv_pending_request = force ? FWUP_REQUEST_FORCE : FWUP_REQUEST_NORMAL;
  // upgrade requested
}

bool touch_fwup_process_pending(void) {
  fwup_request_t request = priv_pending_request;
  if (request == FWUP_REQUEST_NONE) {
    return false;
  }

  priv_pending_request = FWUP_REQUEST_NONE;
  touch_set_fwup_in_progress(true);

  bool result = (request == FWUP_REQUEST_FORCE) ? touch_fwup_force_upgrade() : touch_fwup_upgrade();

  touch_set_fwup_in_progress(false);
  LOGI("Upgrade %s", result ? "succeeded" : "failed");

  return result;
}
