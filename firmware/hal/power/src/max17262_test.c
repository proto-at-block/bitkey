#include "attributes.h"
#include "battery_configs.h"
#include "bitlog.h"
#include "log.h"
#include "max17262.h"
#include "max17262_reg.h"
#include "mcu_i2c.h"

#include <criterion/criterion.h>
#include <criterion/new/assert.h>

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

enum {
  TEST_REGISTER_COUNT = 256,
};

static uint16_t fake_registers[TEST_REGISTER_COUNT];
static uint32_t fake_time_ms;
static bool fake_model_unlocked;
static bool fake_corrupt_verify_read;
static bool fake_verify_corrupted;
static max17262_reg_t fake_fail_write_address;
static uint32_t fake_fail_write_count;
static uint32_t fake_write_len_1[TEST_REGISTER_COUNT];
static uint32_t fake_write_len_2[TEST_REGISTER_COUNT];

mcu_i2c_bus_config_t power_i2c_config = {0};
mcu_i2c_device_t max17262_i2c_config = {0};

static uint16_t fake_register_read(max17262_reg_t address) {
  if (address >= MAX17262_OCVTABLE0 && address < (MAX17262_XTABLE0 + MAX17262_XTABLE_SIZE)) {
    if (!fake_model_unlocked) {
      return 0;
    }

    if (fake_corrupt_verify_read && !fake_verify_corrupted && address == MAX17262_OCVTABLE0) {
      fake_verify_corrupted = true;
      return fake_registers[address] ^ 0x0001;
    }
  }

  return fake_registers[address];
}

static void fake_update_model_lock_state(void) {
  if (fake_registers[MAX17262_REG_UNLOCK_MODEL_STEP_1] == 0x0059 &&
      fake_registers[MAX17262_REG_UNLOCK_MODEL_STEP_2] == 0x00c4) {
    fake_model_unlocked = true;
  } else if (fake_registers[MAX17262_REG_UNLOCK_MODEL_STEP_1] == 0x0000 &&
             fake_registers[MAX17262_REG_UNLOCK_MODEL_STEP_2] == 0x0000) {
    fake_model_unlocked = false;
  }
}

static void fake_register_write(max17262_reg_t address, const uint8_t* data, uint16_t len) {
  if (len == sizeof(uint8_t)) {
    fake_write_len_1[address]++;
    fake_registers[address] &= 0xff00;
    fake_registers[address] |= data[0];
  } else if (len == sizeof(uint16_t)) {
    const uint16_t value = (uint16_t)(data[0] | ((uint16_t)data[1] << 8));
    fake_write_len_2[address]++;
    fake_registers[address] = value;

    if (address == MAX17262_REG_CONFIG2 && (value & MAX17262_REG_CONFIG2_LDMDL_MASK) != 0) {
      fake_registers[address] = (uint16_t)(value & ~MAX17262_REG_CONFIG2_LDMDL_MASK);
    }
  }

  if (address == MAX17262_REG_UNLOCK_MODEL_STEP_1 || address == MAX17262_REG_UNLOCK_MODEL_STEP_2) {
    fake_update_model_lock_state();
  }
}

static void reset_fake_max17262(void) {
  memset(fake_registers, 0, sizeof(fake_registers));
  memset(fake_write_len_1, 0, sizeof(fake_write_len_1));
  memset(fake_write_len_2, 0, sizeof(fake_write_len_2));
  fake_time_ms = 0;
  fake_model_unlocked = false;
  fake_corrupt_verify_read = false;
  fake_verify_corrupted = false;
  fake_fail_write_address = (max17262_reg_t)TEST_REGISTER_COUNT;
  fake_fail_write_count = 0;

  fake_registers[MAX17262_REG_FSTAT] = 0x0000;
  fake_registers[MAX17262_REG_HIBCFG] = 0x0000;
  fake_registers[MAX17262_REG_STATUS] = 0x0002;   // POR set
  fake_registers[MAX17262_REG_CONFIG2] = 0x0010;  // D4 set
}

void _log(log_level_t level, const char* colour, const char* file, int line, const char* format,
          ...) {
  (void)level;
  (void)colour;
  (void)file;
  (void)line;
  (void)format;
}

void _bitlog_record_event(uint16_t event, uint8_t status, void* pc, void* lr) {
  (void)event;
  (void)status;
  (void)pc;
  (void)lr;
}

void rtos_thread_sleep(const uint32_t time_ms) {
  fake_time_ms += time_ms;
}

uint32_t rtos_thread_systime(void) {
  return fake_time_ms;
}

void mcu_i2c_bus_init(const mcu_i2c_bus_config_t* config, const mcu_i2c_device_t* device,
                      const bool enable) {
  (void)config;
  (void)device;
  (void)enable;
}

bool mcu_i2c_transfer_enter_critical(const mcu_i2c_device_t* device) {
  (void)device;
  return true;
}

bool mcu_i2c_transfer_exit_critical(const mcu_i2c_device_t* device) {
  (void)device;
  return true;
}

mcu_i2c_err_t mcu_i2c_transfer(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                               const uint32_t timeout_ms) {
  (void)device;
  (void)timeout_ms;
  const max17262_reg_t address = (max17262_reg_t)seq->buf[0].data[0];

  if (seq->flags == MCU_I2C_FLAG_WRITE_READ) {
    if (seq->buf[1].len == sizeof(uint16_t)) {
      const uint16_t value = fake_register_read(address);
      seq->buf[1].data[0] = (uint8_t)(value & 0x00ff);
      seq->buf[1].data[1] = (uint8_t)((value >> 8) & 0x00ff);
      return MCU_I2C_TRANSFER_DONE;
    }

    if (seq->buf[1].len == sizeof(uint8_t)) {
      seq->buf[1].data[0] = (uint8_t)(fake_register_read(address) & 0x00ff);
      return MCU_I2C_TRANSFER_DONE;
    }
  } else if (seq->flags == MCU_I2C_FLAG_WRITE_WRITE) {
    if (address == fake_fail_write_address && fake_fail_write_count > 0) {
      fake_fail_write_count--;
      return MCU_I2C_TRANSFER_BUS_ERR;
    }

    fake_register_write(address, seq->buf[1].data, seq->buf[1].len);
    return MCU_I2C_TRANSFER_DONE;
  }

  return MCU_I2C_TRANSFER_USAGE_FAULT;
}

mcu_i2c_err_t mcu_i2c_transfer_critical(const mcu_i2c_device_t* device, mcu_i2c_transfer_seq_t* seq,
                                        const uint32_t timeout_ms) {
  return mcu_i2c_transfer(device, seq, timeout_ms);
}

bool battery_get_variant(uint32_t* variant) {
  *variant = BATTERY_VARIANT_DEFAULT;
  return true;
}

TestSuite(max17262_por_initialise);

Test(max17262_por_initialise, seeds_mixcap_and_avgcap_from_vfsoc) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0x1234;
  fake_registers[MAX17262_VFSOC] = 12800;  // 50.0%

  const uint16_t expected_capacity =
    (uint16_t)(((uint32_t)fake_registers[MAX17262_VFSOC] *
                (uint32_t)battery_config_get(BATTERY_VARIANT_DEFAULT)->DesignCap) /
               (uint32_t)MAX17262_VFSOC_MAX);

  cr_assert(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_MIXCAP], expected_capacity);
  cr_assert_eq(fake_registers[MAX17262_REG_AVGCAP], expected_capacity);
  cr_assert_neq(fake_registers[MAX17262_REG_MIXCAP], 0);
}

Test(max17262_por_initialise, uses_word_access_and_restores_hibcfg) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0xabcd;
  fake_registers[MAX17262_VFSOC] = 20480;  // 80.0%

  cr_assert(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_HIBCFG], 0xabcd);
  cr_assert_eq(fake_write_len_1[MAX17262_REG_HIBCFG], 0);
  cr_assert_eq(fake_write_len_1[MAX17262_REG_COMMAND], 0);
  cr_assert_geq(fake_write_len_2[MAX17262_REG_HIBCFG], 2);
  cr_assert_eq(fake_write_len_2[MAX17262_REG_COMMAND], 2);
}

Test(max17262_por_initialise, restores_hibcfg_and_relocks_model_when_model_verify_fails) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0x55aa;
  fake_registers[MAX17262_VFSOC] = 12800;
  fake_corrupt_verify_read = true;

  cr_assert_not(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_HIBCFG], 0x55aa);
  cr_assert_eq(fake_model_unlocked, false);
}

Test(max17262_por_initialise, restores_hibcfg_when_hibernate_exit_write_fails) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0xa55a;
  fake_fail_write_address = MAX17262_REG_COMMAND;
  fake_fail_write_count = 1;

  cr_assert_not(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_HIBCFG], 0xa55a);
  cr_assert_eq(fake_write_len_2[MAX17262_REG_HIBCFG], 1);
  cr_assert_eq(fake_model_unlocked, false);
}

Test(max17262_por_initialise, restores_hibcfg_when_late_capacity_write_fails) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0x1357;
  fake_registers[MAX17262_VFSOC] = 12800;
  fake_fail_write_address = MAX17262_REG_DESIGNCAP;
  fake_fail_write_count = 1;

  cr_assert_not(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_HIBCFG], 0x1357);
  cr_assert_eq(fake_model_unlocked, false);
}

Test(max17262_por_initialise, restores_hibcfg_when_optional_register_write_fails) {
  reset_fake_max17262();
  fake_registers[MAX17262_REG_HIBCFG] = 0x2468;
  fake_registers[MAX17262_VFSOC] = 12800;
  fake_fail_write_address = MAX17262_REG_RELAXCFG;
  fake_fail_write_count = 1;

  cr_assert_not(max17262_por_initialise());
  cr_assert_eq(fake_registers[MAX17262_REG_HIBCFG], 0x2468);
  cr_assert_eq(fake_model_unlocked, false);
}
