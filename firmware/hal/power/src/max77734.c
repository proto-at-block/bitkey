#include "max77734.h"

#include "assert.h"
#include "attributes.h"
#include "bitlog.h"
#include "exti.h"
#include "log.h"
#include "max77734_reg.h"
#include "mcu_i2c.h"

#include <stdint.h>
#include <string.h>

extern mcu_i2c_bus_config_t power_i2c_config;
extern mcu_i2c_device_t max77734_i2c_config;

static const uint32_t transfer_timeout_ms = 50;

static const uint32_t VSYS_REG_DEFAULT = 8u;      /* 4.3V = 4.1V + (8 * 25mV) */
static const uint32_t CHG_CV_DEFAULT = 20u;       /* 4.1V = 3.6V + (20 * 25mV) */
static const uint32_t CHG_CV_JEITA_DEFAULT = 16u; /* 4.0V = 3.6V + (16 * 25mV) */

static const uint32_t VSYS_REG_MAX = 20u;     /* 4.6V = 4.1V + (20 * 25mV) */
static const uint32_t CHG_CV_MAX = 31u;       /* 4.375V = 3.6V + (31 * 25mV) */
static const uint32_t CHG_CV_JEITA_MAX = 24u; /* 4.2V = 3.6V + (24 * 25mV) */

#define PRINT_REGISTER(addr)            \
  {                                     \
    uint8_t value;                      \
    read_register(addr, &value);        \
    printf(#addr " = 0x%02X\n", value); \
  }

static void configure(void);
static bool read_register(const max77734_reg_t address, uint8_t* result);
static bool write_register(const max77734_reg_t address, const uint8_t* data);

void max77734_init(void) {
  mcu_i2c_bus_init(&power_i2c_config, &max77734_i2c_config, true);
  configure();
}

bool max77734_validate(void) {
  max77734_reg_cid_t cid_reg = {0};
  read_register(MAX77734_REG_CID, cid_reg.bytes);
  if (cid_reg.values.CID != MAX77734GENP) {
    LOGE("MAX77734 CID invalid (0x%x)", cid_reg.values.CID);
    BITLOG_EVENT(charger_validate_err, cid_reg.values.CID);
    return false;
  }

  return true;
}

void max77734_irq_enable(exti_config_t* irq) {
  exti_enable(irq);

  // Clear any pending interrupts
  max77734_reg_int_chg_t int_charge = {0};
  if (!read_register(MAX77734_REG_INT_CHG, int_charge.bytes)) {
    LOGE("error reading INT_CHG");
  }

  // Enable the interrupts
  max77734_reg_int_m_chg_t int_m_chg = {.bytes[0] = 0xff};
  int_m_chg.values.CHG_M = 0;
  int_m_chg.values.CHGIN_M = 0;
  write_register(MAX77734_REG_INT_M_CHG, int_m_chg.bytes);
}

bool max77734_irq_wait(exti_config_t* irq, uint32_t timeout_ms) {
  if (exti_wait(irq, timeout_ms, true)) {
    max77734_irq_clear();
    return true;
  }
  return false;
}

void max77734_irq_clear(void) {
  // Read interrupt register to clear the interrupt flag
  max77734_reg_int_chg_t int_charge = {0};
  if (!read_register(MAX77734_REG_INT_CHG, int_charge.bytes)) {
    LOGE("unable to read INT_CHG");
  }
}

void max77734_charge_enable(const bool enabled) {
  max77734_reg_cnfg_chg_b_t b_reg = {0};
  read_register(MAX77734_REG_CNFG_CHG_B, b_reg.bytes);
  b_reg.values.CHG_EN = enabled;
  write_register(MAX77734_REG_CNFG_CHG_B, b_reg.bytes);
}

void max77734_set_max_charge_cv(const bool max) {
  max77734_reg_cnfg_chg_d_t d_reg = {0};
  max77734_reg_cnfg_chg_g_t g_reg = {0};
  max77734_reg_cnfg_chg_h_t h_reg = {0};

  read_register(MAX77734_REG_CNFG_CHG_D, d_reg.bytes);
  read_register(MAX77734_REG_CNFG_CHG_G, g_reg.bytes);
  read_register(MAX77734_REG_CNFG_CHG_H, h_reg.bytes);

  if (max) {
    // Increase VSYS_REG before increasing CHG_CV and CHG_CV_JEITA
    d_reg.values.VSYS_REG = VSYS_REG_MAX;
    write_register(MAX77734_REG_CNFG_CHG_D, d_reg.bytes);

    g_reg.values.CHG_CV = CHG_CV_MAX;
    h_reg.values.CHG_CV_JEITA = CHG_CV_JEITA_MAX;
  } else {
    g_reg.values.CHG_CV = CHG_CV_DEFAULT;
    h_reg.values.CHG_CV_JEITA = CHG_CV_JEITA_DEFAULT;

    // Decrease VSYS_REG after decreasing CHG_CV and CHG_CV_JEITA
    d_reg.values.VSYS_REG = VSYS_REG_DEFAULT;
    write_register(MAX77734_REG_CNFG_CHG_D, d_reg.bytes);
  }

  write_register(MAX77734_REG_CNFG_CHG_G, g_reg.bytes);
  write_register(MAX77734_REG_CNFG_CHG_H, h_reg.bytes);
}

void max77734_fast_charge(void) {
  const max77734_reg_cnfg_chg_e_t e = {
    .values =
      {
        .T_FAST_CHG = 0b11, /* 7 hours */
        .CHG_CC = 39,       /* (39 * 7.5mA) + 7.5 = 300mA */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_E, e.bytes);
}

void max77734_charging_status(bool* charging, bool* chgin_valid) {
  max77734_reg_stat_chg_b_t stat_chg_b = {0};
  read_register(MAX77734_REG_STAT_CHG_B, stat_chg_b.bytes);

  *charging = stat_chg_b.values.CHG;
  *chgin_valid = (stat_chg_b.values.CHGIN_DTLS == 0b11);
}

static void configure(void) {
  const max77734_reg_cnfg_chg_a_t a = {
    .values =
      {
        .THM_COLD = 0b10,
        .THM_COOL = 0b11,
        .THM_WARM = 0b10,
        .THM_HOT = 0b11,
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_A, a.bytes);

  const max77734_reg_cnfg_chg_b_t b = {
    .values =
      {
        .CHG_EN = false,
        .I_PQ = 0,
        .ICHGIN_LIM = 0b100, /* 475ma/95ma */
        .VCHGIN_MIN = 0,     /* 4.0V */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_B, b.bytes);

  const max77734_reg_cnfg_chg_c_t c = {
    .values =
      {
        .T_TOPOFF = 0b111, /* 35 minutes */
        .I_TERM = 1,       /* 7.5% = 15mA */
        .CHG_PQ = 0b111,   /* 3.0V */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_C, c.bytes);

  const max77734_reg_cnfg_chg_d_t d = {
    .values =
      {
        .VSYS_REG = VSYS_REG_DEFAULT, /* 4.3V = 4.1V + (8 * 25mV) */
        .TJ_REG = 0,                  /* 60°C */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_D, d.bytes);

  const max77734_reg_cnfg_chg_e_t e = {
    .values =
      {
        .T_FAST_CHG = 0b11, /* 7 hours */
        .CHG_CC = 19,       /* (19 * 7.5mA) + 7.5 = 150mA */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_E, e.bytes);

  const max77734_reg_cnfg_chg_f_t f = {
    .values =
      {
        .THM_EN = 1,       /* Thermistor enabled */
        .CHG_CC_JEITA = 9, /* (9 * 7.5mA) + 7.5mA = 75mA */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_F, f.bytes);

  const max77734_reg_cnfg_chg_g_t g = {
    .values =
      {
        .USBS = 0,                /* USB suspend mode disabled */
        .CHG_CV = CHG_CV_DEFAULT, /* 4.1V = 3.6V + (20 * 25mV) */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_G, g.bytes);

  const max77734_reg_cnfg_chg_h_t h = {
    .values =
      {
        .CHG_CV_JEITA = CHG_CV_JEITA_DEFAULT, /* 4.0V = 3.6V + (16 * 25mV) */
      },
  };
  write_register(MAX77734_REG_CNFG_CHG_H, h.bytes);

  const max77734_reg_cnfg_ldo_b_t ldo_b = {
    .values =
      {
        .LDO_EN = 0b00, /* LDO enables when nENLDO asserts or when CHGIN is valid */
        .LDO_PM = 0,    /* Forced low-power mode */
      },
  };
  write_register(MAX77734_REG_CNFG_LDO_B, ldo_b.bytes);
}

void max77734_print_registers(void) {
  printf("Interrupts and Status\n");
  PRINT_REGISTER(MAX77734_REG_INT_GLBL);
  PRINT_REGISTER(MAX77734_REG_INT_CHG);
  PRINT_REGISTER(MAX77734_REG_STAT_CHG_A);
  PRINT_REGISTER(MAX77734_REG_STAT_CHG_B);
  PRINT_REGISTER(MAX77734_REG_ERCFLAG);
  PRINT_REGISTER(MAX77734_REG_STAT_GLBL);
  PRINT_REGISTER(MAX77734_REG_INTM_GLBL);
  PRINT_REGISTER(MAX77734_REG_INT_M_CHG);
  rtos_thread_sleep(20);

  printf("\nGlobal Config\n");
  PRINT_REGISTER(MAX77734_REG_CNFG_GLBL);
  PRINT_REGISTER(MAX77734_REG_CID);
  PRINT_REGISTER(MAX77734_REG_CNFG_WDT);
  rtos_thread_sleep(20);

  printf("\nCharger Config\n");
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_A);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_B);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_C);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_D);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_E);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_F);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_G);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_H);
  PRINT_REGISTER(MAX77734_REG_CNFG_CHG_I);
  rtos_thread_sleep(20);

  printf("\nLDO Config\n");
  PRINT_REGISTER(MAX77734_REG_CNFG_LDO_A);
  PRINT_REGISTER(MAX77734_REG_CNFG_LDO_B);
  rtos_thread_sleep(20);

  printf("\nSink Config\n");
  PRINT_REGISTER(MAX77734_REG_CNFG_SNK1_A);
  PRINT_REGISTER(MAX77734_REG_CNFG_SNK1_B);
  PRINT_REGISTER(MAX77734_REG_CNFG_SNK2_A);
  PRINT_REGISTER(MAX77734_REG_CNFG_SNK2_B);
  PRINT_REGISTER(MAX77734_REG_CNFG_SNK_TOP);
  rtos_thread_sleep(20);
}

void max77734_print_status(void) {
  max77734_reg_stat_chg_b_t stat_chg_b = {0};
  read_register(MAX77734_REG_STAT_CHG_B, stat_chg_b.bytes);

  if (stat_chg_b.values.CHG) {
    LOGD("Battery is charging");
  } else {
    LOGD("Battery is not charging");
  }
}

void max77734_print_mode(void) {
  max77734_reg_stat_chg_b_t stat_chg_b = {0};
  read_register(MAX77734_REG_STAT_CHG_B, stat_chg_b.bytes);

  LOGD("Charger Mode:");
  switch (stat_chg_b.values.CHG_DTLS) {
    case 0:
      LOGD("Charger off");
      break;
    case 1:
      LOGD("Prequalification mode");
      break;
    case 2:
      LOGD("Fast-charge constant-current (CC) mode");
      break;
    case 3:
      LOGD("JEITA-modified fast-charge constant-current mode");
      break;
    case 4:
      LOGD("Fast-charge constant-voltage (CV) mode");
      break;
    case 5:
      LOGD("JEITA-modified fast-charge constant-voltage mode");
      break;
    case 6:
      LOGD("Top-off mode");
      break;
    case 7:
      LOGD("JEITA-modified top-off mode");
      break;
    case 8:
      LOGD("Done");
      break;
    case 9:
      LOGD("JEITA-modified done");
      break;
    case 10:
      LOGD("Prequalification timer fault");
      break;
    case 11:
      LOGD("Fast-charge timer fault");
      break;
    case 12:
      LOGD("Battery temperature fault");
      break;
    default:
      LOGD("Charger state is invalid");
      break;
  }
}

static bool read_register(const max77734_reg_t address, uint8_t* result) {
  uint8_t tx_buf[1] = {address};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = result, .len = 1},
    .flags = MCU_I2C_FLAG_WRITE_READ,
  };

  const mcu_i2c_err_t ret = mcu_i2c_transfer(&max77734_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}

static bool write_register(const max77734_reg_t address, const uint8_t* data) {
  uint8_t tx_buf[1] = {address};
  mcu_i2c_transfer_seq_t seq = {
    .buf[0] = {.data = tx_buf, .len = 1},
    .buf[1] = {.data = (uint8_t*)data, .len = 1},
    .flags = MCU_I2C_FLAG_WRITE_WRITE,
  };

  const mcu_i2c_err_t ret = mcu_i2c_transfer(&max77734_i2c_config, &seq, transfer_timeout_ms);

  return ret == MCU_I2C_TRANSFER_DONE;
}
