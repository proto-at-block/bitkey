#include "max17262.h"

#include "arithmetic.h"
#include "assert.h"
#include "attributes.h"
#include "battery.h"
#include "battery_configs.h"
#include "bitlog.h"
#include "log.h"
#include "max17262_reg.h"
#include "mcu_i2c.h"

#include <limits.h>

enum {
  MAX17262_POR_INIT_ERR_FSTAT_TIMEOUT = 1,
  MAX17262_POR_INIT_ERR_OCVTABLE_WRITE,
  MAX17262_POR_INIT_ERR_XTABLE_WRITE,
  MAX17262_POR_INIT_ERR_OCVTABLE_VERIFY,
  MAX17262_POR_INIT_ERR_XTABLE_VERIFY,
  MAX17262_POR_INIT_ERR_OCVTABLE_READ_LOCKED,
  MAX17262_POR_INIT_ERR_OCVTABLE_LOCKED,
  MAX17262_POR_INIT_ERR_XTABLE_READ_LOCKED,
  MAX17262_POR_INIT_ERR_XTABLE_LOCKED,
  MAX17262_POR_INIT_ERR_CAPACITY_CLEAR,
  MAX17262_POR_INIT_ERR_CAPACITY_WRITE,
  MAX17262_POR_INIT_ERR_LEARNCFG_WRITE,
  MAX17262_POR_INIT_ERR_LOAD_MODEL,
  MAX17262_POR_INIT_ERR_CYCLES_WRITE,
  MAX17262_POR_INIT_ERR_MODELGAUGE_LOAD,
  MAX17262_POR_INIT_ERR_STATUS_READ,
  MAX17262_POR_INIT_ERR_STATUS_WRITE,
};

#define READ_REGISTER_16_AND_LOG_ON_FAIL(register, buffer) \
  do {                                                     \
    if (!read_register_16(register, buffer) != 0) {        \
      LOGE("Failed to read_register_16: %s", #register);   \
      BITLOG_EVENT(fuel_gauge_reg_read_err, register);     \
      return false;                                        \
    }                                                      \
  } while (0)

#define WRITE_REGISTER_16_AND_LOG_ON_FAIL(register, buffer) \
  do {                                                      \
    if (!write_register_16(register, buffer) != 0) {        \
      LOGE("Failed to write_register_16: %s", #register);   \
      BITLOG_EVENT(fuel_gauge_reg_write_err, register);     \
      return false;                                         \
    }                                                       \
  } while (0)

#define WRITE_REGISTER_AND_LOG_ON_FAIL(register, buffer) \
  do {                                                   \
    if (!write_register(register, buffer) != 0) {        \
      LOGE("Failed to write_register: %s", #register);   \
      BITLOG_EVENT(fuel_gauge_reg_write_err, register);  \
      return false;                                      \
    }                                                    \
  } while (0)

extern mcu_i2c_bus_config_t power_i2c_config;
extern mcu_i2c_device_t max17262_i2c_config;

#define TIMED_OUT_MS(s, t) ((rtos_thread_systime() - s) > t)
static const uint32_t transfer_timeout_ms = 50u;
static const uint32_t fstat_timeout_ms = 100u;
static const uint32_t wait_loop_delay_ms = 10u;

static const uint32_t model_capacity_attempts = 3u;        // As recommended by implementation guide
static const uint32_t model_load_attempts = 60000u / 10u;  // 60 seconds

static bool modelgauge_configured(void);
static bool read_register(const max17262_reg_t address, uint8_t* result, const size_t len);
static bool read_register_16(const max17262_reg_t address, uint8_t* result);
static bool write_register(const max17262_reg_t address, const uint8_t data);
static bool write_register_16(const max17262_reg_t address, const uint8_t* data);
static bool write_register_16_verify(const max17262_reg_t address, const uint8_t* data);
static bool write_register_16_critical(const max17262_reg_t address, const uint8_t* data);
static bool write_table_16(const max17262_reg_t address, const uint16_t* data);
static bool read_table_16(const max17262_reg_t address, uint16_t* data);

static bool modelgauge_unlock(void);
static bool modelgauge_lock(void);
static bool ez_config_option_3(uint8_t* hibcfg, battery_variant_t variant);

max17262_status_t max17262_init(void) {
  mcu_i2c_bus_init(&power_i2c_config, &max17262_i2c_config, true);

  // Step 0 - Check POR
  max17262_reg_status_t status = {0};
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_STATUS, &status.bytes[0]);
  if (status.values.POR) {
    return MAX17262_STATUS_POWER_ON_RESET;
  }

  // Some units were shipped before modelgauge was supported. Detect those units for a POR
  if (!modelgauge_configured()) {
    return MAX17262_STATUS_MODELGAUGE_UNINITIALISED;
  }

  if (!max17262_validate()) {
    return MAX17262_STATUS_FAILED;
  }

  return MAX17262_STATUS_OK;
}

bool max17262_validate(void) {
  max17262_devname_t devname = 0;
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DEVNAME, (uint8_t*)&devname);

  if (devname != MAX17262_DEVNAME) {
    BITLOG_EVENT(fuel_gauge_validate_err, devname);
    return false;
  }

  return true;
}

uint32_t max17262_soc_millipercent(void) {
  uint16_t soc = {0};

  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_REPSOC, (uint8_t*)&soc);
  const uint32_t scaled_soc = MAX17262_REG_PERCENT_IN_MILLIPERCENT(soc);

  // Clip SOC to 100.000% max
  const uint32_t soc_max = 100 * 1000;
  if (scaled_soc > soc_max) {
    return soc_max;
  }

  return scaled_soc;
}

uint32_t max17262_vcell_mv(void) {
  uint16_t vcell = {0};

  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_VCELL, (uint8_t*)&vcell);
  return MAX17262_REG_VOLTS_IN_MV(vcell);
}

bool max17262_por_initialise(void) {
  // Step 1 - Delay until FSTAT.DNR bit == 0
  max17262_reg_fstat_t fstat = {0};
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_FSTAT, &fstat.bytes[0]);

  uint32_t start = rtos_thread_systime();
  while (fstat.values.DNR) {
    rtos_thread_sleep(wait_loop_delay_ms);
    if (!read_register_16(MAX17262_REG_FSTAT, &fstat.bytes[0]) ||
        TIMED_OUT_MS(start, fstat_timeout_ms)) {
      LOGE("Error reading MAX17262_REG_FSTAT DNR");
      BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_FSTAT_TIMEOUT);
      return false;
    }
  }

  // Step 2 - Initialize Configuration
  // Store original HibCFG value
  uint8_t hibcfg = 0;
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_HIBCFG, &hibcfg);

  WRITE_REGISTER_AND_LOG_ON_FAIL(MAX17262_REG_COMMAND, 0x90);  // Exit Hibernate Mode step 1
  WRITE_REGISTER_AND_LOG_ON_FAIL(MAX17262_REG_HIBCFG, 0x00);   // Exit Hibernate Mode step 2
  WRITE_REGISTER_AND_LOG_ON_FAIL(MAX17262_REG_COMMAND, 0x00);  // Exit Hibernate Mode step 3

  // Get configured battery variant from filesystem
  battery_variant_t variant = BATTERY_VARIANT_DEFAULT;
  if (!battery_get_variant((uint32_t*)&variant)) {
    LOGW("Unable to read battery variant");
  }

  // Modelgauge M5 Option 3
  if (!ez_config_option_3(&hibcfg, variant)) {
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_MODELGAUGE_LOAD);
    return false;
  }

  // Step 3: Initialization Complete
  max17262_reg_status_t status = {0};
  if (!read_register_16(MAX17262_REG_STATUS, &status.bytes[0])) {
    LOGE("Error reading status register");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_STATUS_READ);
    return false;
  }

  status.values.POR = 0;
  if (!write_register_16_verify(MAX17262_REG_STATUS, &status.bytes[0])) {
    LOGE("Error resetting POR bit in status register");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_STATUS_WRITE);
    return false;
  }

  return true;
}

int32_t max17262_average_current(void) {
  uint16_t avg_current_raw = {0};

  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_AVGCURRENT, (uint8_t*)&avg_current_raw);
  int32_t avg_current = (int32_t)avg_current_raw;
  if (avg_current > SHRT_MAX) {
    avg_current -= 0x10000;
  }

  return MAX17262_REG_CURRENT_UNIT_AS_MILLIAMPS(avg_current);
}

uint32_t max17262_cycles(void) {
  uint16_t cycles = {0};
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_CYCLES, (uint8_t*)&cycles);
  return cycles;
}

void max17262_get_regdump(max17262_regdump_t* registers_out) {
  registers_out->soc = max17262_soc_millipercent();
  registers_out->vcell = max17262_vcell_mv();
  registers_out->avg_current = max17262_average_current();
  registers_out->cycles = max17262_cycles();
}

bool max17262_clear_modelgauge(void) {
  // Unlock model access
  if (!modelgauge_unlock()) {
    return false;
  }

  // Clear OCVTable and XTable
  const uint16_t ocvtable_clear[16] = {0};
  const uint16_t xtable_clear[16] = {0};
  if (!write_table_16(MAX17262_OCVTABLE0, ocvtable_clear) ||
      !write_table_16(MAX17262_XTABLE0, xtable_clear)) {
    LOGE("Error clearing OCVTable and XTable");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_OCVTABLE_WRITE);
    return false;
  }

  // Lock model access
  if (!modelgauge_lock()) {
    return false;
  }

  return true;
}

static bool modelgauge_configured(void) {
  // Unlock model access
  if (!modelgauge_unlock()) {
    return false;
  }

  // If a modelgauge is not configured, the first two bytes of the ocvtable register will be 0x0000
  uint8_t ocvtable[2] = {0xff, 0xff};
  if (!read_register(MAX17262_OCVTABLE0, ocvtable, sizeof(ocvtable))) {
    return false;
  }

  // Lock model access
  if (!modelgauge_lock()) {
    return false;
  }

  return !(ocvtable[0] == 0x00 && ocvtable[1] == 0x00);
}

static bool read_register(const max17262_reg_t address, uint8_t* result, const size_t len) {
  uint8_t tx_buf[1] = {address};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = result, .len = len},
    .flags = MCU_I2C_FLAG_WRITE_READ,
  };

  const mcu_i2c_err_t ret = mcu_i2c_transfer(&max17262_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}

static bool read_register_16(const max17262_reg_t address, uint8_t* result) {
  return read_register(address, result, sizeof(uint16_t));
}

static bool write_register(const max17262_reg_t address, const uint8_t data) {
  uint8_t tx_buf[1] = {address};
  uint8_t tx_data[1] = {data};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = tx_data, .len = 1},
    .flags = MCU_I2C_FLAG_WRITE_WRITE,
  };

  const mcu_i2c_err_t ret = mcu_i2c_transfer(&max17262_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}

static bool write_register_16(const max17262_reg_t address, const uint8_t* data) {
  uint8_t tx_buf[1] = {address};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = (uint8_t*)data, .len = sizeof(uint16_t)},
    .flags = MCU_I2C_FLAG_WRITE_WRITE,
  };

  const mcu_i2c_err_t ret = mcu_i2c_transfer(&max17262_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}

static bool write_register_16_verify(const max17262_reg_t address, const uint8_t* data) {
  // Write data
  if (!write_register_16(address, data)) {
    return false;
  }

  // Read data
  uint8_t data_read[sizeof(uint16_t)] = {0};
  if (!read_register(address, data_read, sizeof(uint16_t))) {
    return false;
  }

  // Verify data
  if (data[0] != data_read[0] || data[1] != data_read[1]) {
    return false;
  }

  return true;
}

static bool write_register_16_critical(const max17262_reg_t address, const uint8_t* data) {
  uint8_t tx_buf[1] = {address};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = (uint8_t*)data, .len = sizeof(uint16_t)},
    .flags = MCU_I2C_FLAG_WRITE_WRITE,
  };

  const mcu_i2c_err_t ret =
    mcu_i2c_transfer_critical(&max17262_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}

static bool write_table_16(const max17262_reg_t address, const uint16_t* data) {
  for (size_t i = 0; i < 16; i++) {
    if (!write_register_16(address + i, (const uint8_t*)&data[i])) {
      LOGE("Error writing table %02x at position %02x", address, i);
      return false;
    }
  }

  return true;
}

static bool read_table_16(const max17262_reg_t address, uint16_t* data) {
  for (size_t i = 0; i < 16; i++) {
    if (!read_register_16(address + i, (uint8_t*)&data[i])) {
      LOGE("Error reading table %02x at position %02x", address, i);
      return false;
    }
  }

  return true;
}

static bool modelgauge_unlock(void) {
  const uint8_t unlock1[2] = {0x59, 0x00};
  const uint8_t unlock2[2] = {0xC4, 0x00};

  mcu_i2c_transfer_enter_critical(&max17262_i2c_config);
  if (!write_register_16_critical(MAX17262_REG_UNLOCK_MODEL_STEP_1, unlock1) ||
      !write_register_16_critical(MAX17262_REG_UNLOCK_MODEL_STEP_2, unlock2)) {
    goto err;
  }
  mcu_i2c_transfer_exit_critical(&max17262_i2c_config);

  return true;

err:
  mcu_i2c_transfer_exit_critical(&max17262_i2c_config);
  return false;
}

static bool modelgauge_lock(void) {
  const uint8_t lock[2] = {0x00, 0x00};

  mcu_i2c_transfer_enter_critical(&max17262_i2c_config);
  if (!write_register_16_critical(MAX17262_REG_UNLOCK_MODEL_STEP_1, lock) ||
      !write_register_16_critical(MAX17262_REG_UNLOCK_MODEL_STEP_2, lock)) {
    goto err;
  }
  mcu_i2c_transfer_exit_critical(&max17262_i2c_config);

  return true;

err:
  mcu_i2c_transfer_exit_critical(&max17262_i2c_config);
  return false;
}

// Custom Full INI with OCV Table
static bool ez_config_option_3(uint8_t* hibcfg, battery_variant_t variant) {
  // Unlock model access
  if (!modelgauge_unlock()) {
    return false;
  }

  // Get the model
  max17262_modelgauge_t* model = battery_config_get(variant);

  // Write the custom model
  if (!write_table_16(MAX17262_OCVTABLE0, (const uint16_t*)model->OCVTable)) {
    LOGE("Error writing OCVTable");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_OCVTABLE_WRITE);
    return false;
  }
  if (!write_table_16(MAX17262_XTABLE0, (const uint16_t*)model->XTable)) {
    LOGE("Error writing XTable");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_XTABLE_WRITE);
    return false;
  }

  // Read back and verify the model
  uint16_t ocvtable[MAX17262_OCVTABLE_SIZE] = {0};
  const bool ocvtable_read_ok = read_table_16(MAX17262_OCVTABLE0, ocvtable);
  if (!ocvtable_read_ok || memcmp(ocvtable, model->OCVTable, sizeof(ocvtable)) != 0) {
    LOGE("Error verifying OCVTable");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_OCVTABLE_VERIFY);
    return false;
  }

  uint16_t xtable[MAX17262_XTABLE_SIZE] = {0};
  const bool xtable_read_ok = read_table_16(MAX17262_XTABLE0, xtable);
  if (!xtable_read_ok || memcmp(xtable, model->XTable, sizeof(xtable)) != 0) {
    LOGE("Error verifying XTable");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_XTABLE_VERIFY);
    return false;
  }

  // Lock model access
  if (!modelgauge_lock()) {
    return false;
  }

  // Verify model is locked
  // Read 16 words from the ocvtable register and verify each word is 0x0000
  memset(ocvtable, 0, sizeof(ocvtable));
  const bool ocvtable_read_locked_ok = read_table_16(MAX17262_OCVTABLE0, ocvtable);
  if (!ocvtable_read_locked_ok) {
    LOGE("Error verifying OCVTable is locked");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_OCVTABLE_READ_LOCKED);
    return false;
  }
  for (size_t i = 0; i < MAX17262_OCVTABLE_SIZE; i++) {
    if (ocvtable[i] != 0) {
      LOGE("Error verifying OCVTable is locked");
      BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_OCVTABLE_LOCKED);
      return false;
    }
  }

  // Read 16 words from the xtable register and verify each word is 0x0000
  memset(xtable, 0, sizeof(xtable));
  const bool xtable_read_locked_ok = read_table_16(MAX17262_XTABLE0, xtable);
  if (!xtable_read_locked_ok) {
    LOGE("Error verifying XTable is locked");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_XTABLE_READ_LOCKED);
    return false;
  }
  for (size_t i = 0; i < MAX17262_XTABLE_SIZE; i++) {
    if (xtable[i] != 0) {
      LOGE("Error verifying XTable is locked");
      BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_XTABLE_LOCKED);
      return false;
    }
  }

  // Write Custom Parameters
  rtos_thread_sleep(100);
  const uint8_t repcap_clear[2] = {0x00, 0x00};
  if (!write_register_16_verify(MAX17262_REG_REPCAP, repcap_clear)) {  // Clear RepCap
    LOGE("Error writing RepCap and DesignCap");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_CAPACITY_CLEAR);
    return false;
  }
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DESIGNCAP, (const uint8_t*)&model->DesignCap);

  // No saved history exists, so we load the design values
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_FULLCAPREP, (const uint8_t*)&model->DesignCap);
  for (uint32_t attempt = 0; attempt < model_capacity_attempts; attempt++) {
    WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DQACC, (const uint8_t*)&model->dQacc);
    WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DPACC, (const uint8_t*)&model->dPacc);
    rtos_thread_sleep(10);
    WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_FULLCAPNOM, (const uint8_t*)&model->DesignCap);

    uint16_t fullcapnom, dqacc, dpacc = 0;
    READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_FULLCAPNOM, (uint8_t*)&fullcapnom);
    READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DQACC, (uint8_t*)&dqacc);
    READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_DPACC, (uint8_t*)&dpacc);
    if (fullcapnom == model->DesignCap && dqacc == model->dQacc && dpacc == model->dPacc) {
      break;
    } else if (attempt == (model_capacity_attempts - 1)) {
      LOGE("Error writing fullcapnom, dqacc, dpacc");
      BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_CAPACITY_WRITE);
      return false;
    }
  }

  uint16_t vfsoc = 0;
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_VFSOC, (uint8_t*)&vfsoc);
  const uint16_t update_capacity =
    (uint16_t)((uint32_t)vfsoc / ((uint32_t)MAX17262_VFSOC_MAX * (uint32_t)model->DesignCap));

  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_MIXCAP, (const uint8_t*)&update_capacity);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_AVGCAP, (const uint8_t*)&update_capacity);
  rtos_thread_sleep(200);

  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_ICHGTERM, (const uint8_t*)&model->ICHGTerm);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_VEMPTY, (const uint8_t*)&model->Vempty);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_RCOMP0, (const uint8_t*)&model->RCOMP0);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_TEMPCO, (const uint8_t*)&model->TempCo);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_QRTABLE00, (const uint8_t*)&model->QRTable00);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_QRTABLE10, (const uint8_t*)&model->QRTable10);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_QRTABLE20, (const uint8_t*)&model->QRTable20);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_QRTABLE30, (const uint8_t*)&model->QRTable30);

  // Update optional registers
  if (!write_register_16_verify(MAX17262_REG_LEARNCFG, (const uint8_t*)&model->learncfg)) {
    LOGE("Error writing learn config");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_LEARNCFG_WRITE);
    return false;
  }
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_RELAXCFG, (const uint8_t*)&model->relaxcfg);
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_MISCCFG, (const uint8_t*)&model->misccfg);

  // Initiate Model Loading
  uint16_t config2 = 0;
  READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_CONFIG2, (uint8_t*)&config2);
  config2 |= MAX17262_REG_CONFIG2_LDMDL_MASK;  // Set LdMdl bit
  WRITE_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_CONFIG2, (uint8_t*)&config2);

  // Poll the LdMdl bit in the Config2 register, proceed when LdMdl bit becomes 0
  config2 = 0;
  for (uint32_t attempt = 0; attempt < model_load_attempts; attempt++) {
    READ_REGISTER_16_AND_LOG_ON_FAIL(MAX17262_REG_CONFIG2, (uint8_t*)&config2);
    if ((config2 & MAX17262_REG_CONFIG2_LDMDL_MASK) == 0) {
      break;
    } else if (attempt == (model_load_attempts - 1)) {
      LOGE("Error verifying LdMdl bit is 0");
      BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_LOAD_MODEL);
      return false;
    }

    // These two register addresses are undocumented, but are required to be written by the
    // modelgauge m5 implementation guide
    const uint8_t unknown_clear[2] = {0x00, 0x00};
    WRITE_REGISTER_16_AND_LOG_ON_FAIL(0x0A, (const uint8_t*)unknown_clear);
    WRITE_REGISTER_16_AND_LOG_ON_FAIL(0x0B, (const uint8_t*)unknown_clear);
    rtos_thread_sleep(10);
  }

  // Update QRTable20, QRTable30 and Cycles
  const uint16_t cycles = 0;
  if (!write_register_16_verify(MAX17262_REG_QRTABLE20, (const uint8_t*)&model->QRTable20) ||
      !write_register_16_verify(MAX17262_REG_QRTABLE30, (const uint8_t*)&model->QRTable30) ||
      !write_register_16_verify(MAX17262_REG_CYCLES, (const uint8_t*)&cycles)) {
    LOGE("Error writing QRTable20, QRTable30, Cycles");
    BITLOG_EVENT(fuel_gauge_por_init_err, MAX17262_POR_INIT_ERR_CYCLES_WRITE);
    return false;
  }

  // Restore HibCFG
  WRITE_REGISTER_AND_LOG_ON_FAIL(MAX17262_REG_HIBCFG, *hibcfg);

  return true;
}
