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

static void configure(const power_ldo_config_t* ldo_config);
static bool read_register(const max77734_reg_t address, uint8_t* result);
static bool write_register(const max77734_reg_t address, const uint8_t* data);

void max77734_init(const power_ldo_config_t* ldo_config) {
  mcu_i2c_bus_init(&power_i2c_config, &max77734_i2c_config, true);
  configure(ldo_config);
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

void max77734_usb_suspend(const bool enabled) {
  max77734_reg_cnfg_chg_g_t g_reg = {0};
  read_register(MAX77734_REG_CNFG_CHG_G, g_reg.bytes);
  g_reg.values.USBS = enabled;
  write_register(MAX77734_REG_CNFG_CHG_G, g_reg.bytes);
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

static void configure(const power_ldo_config_t* ldo_config) {
  ASSERT(ldo_config != NULL);

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
        .LDO_EN = ldo_config->ldo_en & 0b11,
        .LDO_PM = ldo_config->ldo_pm & 0b11,
      },
  };
  write_register(MAX77734_REG_CNFG_LDO_B, ldo_b.bytes);
}

void max77734_set_ldo_low_power_mode(void) {
  max77734_reg_cnfg_ldo_b_t ldo_b = {0};
  if (!read_register(MAX77734_REG_CNFG_LDO_B, ldo_b.bytes)) {
    LOGE("Failed to read LDO_B register");
    return;
  }
  ldo_b.values.LDO_PM = MAX77734_LDO_PM_LOW_POWER;
  if (!write_register(MAX77734_REG_CNFG_LDO_B, ldo_b.bytes)) {
    LOGE("Failed to write LDO_B register");
  }
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
    case MAX77734_CHG_DTLS_OFF:
      LOGD("Charger off");
      break;
    case MAX77734_CHG_DTLS_PREQUALIFICATION:
      LOGD("Prequalification mode");
      break;
    case MAX77734_CHG_DTLS_CC:
      LOGD("Fast-charge constant-current (CC) mode");
      break;
    case MAX77734_CHG_DTLS_JEITA_CC:
      LOGD("JEITA-modified fast-charge constant-current mode");
      break;
    case MAX77734_CHG_DTLS_CV:
      LOGD("Fast-charge constant-voltage (CV) mode");
      break;
    case MAX77734_CHG_DTLS_JEITA_CV:
      LOGD("JEITA-modified fast-charge constant-voltage mode");
      break;
    case MAX77734_CHG_DTLS_TOP_OFF:
      LOGD("Top-off mode");
      break;
    case MAX77734_CHG_DTLS_JEITA_TOP_OFF:
      LOGD("JEITA-modified top-off mode");
      break;
    case MAX77734_CHG_DTLS_DONE:
      LOGD("Done");
      break;
    case MAX77734_CHG_DTLS_JEITA_DONE:
      LOGD("JEITA-modified done");
      break;
    case MAX77734_CHG_DTLS_PREQUALIFICATION_TIMER_FAULT:
      LOGD("Prequalification timer fault");
      break;
    case MAX77734_CHG_DTLS_FAST_CHARGE_TIMER_FAULT:
      LOGD("Fast-charge timer fault");
      break;
    case MAX77734_CHG_DTLS_BATTERY_TEMP_FAULT:
      LOGD("Battery temperature fault");
      break;
    default:
      LOGD("Charger state is invalid");
      break;
  }
}

power_charger_mode_t max77734_get_mode(void) {
  max77734_reg_stat_chg_b_t stat_chg_b = {0};
  read_register(MAX77734_REG_STAT_CHG_B, stat_chg_b.bytes);

  switch (stat_chg_b.values.CHG_DTLS) {
    case MAX77734_CHG_DTLS_OFF:
      return POWER_CHARGER_MODE_OFF;
    case MAX77734_CHG_DTLS_PREQUALIFICATION:
      return POWER_CHARGER_MODE_PREQUAL;
    case MAX77734_CHG_DTLS_CC:
      return POWER_CHARGER_MODE_CC;
    case MAX77734_CHG_DTLS_JEITA_CC:
      return POWER_CHARGER_MODE_JEITA_CC;
    case MAX77734_CHG_DTLS_CV:
      return POWER_CHARGER_MODE_CV;
    case MAX77734_CHG_DTLS_JEITA_CV:
      return POWER_CHARGER_MODE_JEITA_CV;
    case MAX77734_CHG_DTLS_TOP_OFF:
      return POWER_CHARGER_MODE_TOP_OFF;
    case MAX77734_CHG_DTLS_JEITA_TOP_OFF:
      return POWER_CHARGER_MODE_JEITA_TOP_OFF;
    case MAX77734_CHG_DTLS_DONE:
      return POWER_CHARGER_MODE_DONE;
    case MAX77734_CHG_DTLS_JEITA_DONE:
      return POWER_CHARGER_MODE_JEITA_DONE;
    case MAX77734_CHG_DTLS_PREQUALIFICATION_TIMER_FAULT:
      return POWER_CHARGER_MODE_PREQUAL_TIMEOUT;
    case MAX77734_CHG_DTLS_FAST_CHARGE_TIMER_FAULT:
      return POWER_CHARGER_MODE_FAST_CHARGE_TIMEOUT;
    case MAX77734_CHG_DTLS_BATTERY_TEMP_FAULT:
      return POWER_CHARGER_MODE_TEMP_FAULT;
    default:
      break;
  }
  return POWER_CHARGER_MODE_INVALID;
}

void max77734_enable_thermal_interrupts(void) {
  // Read current interrupt masks
  max77734_reg_intm_glbl_t intm_glbl = {.bytes[0] = 0xff};
  if (!read_register(MAX77734_REG_INTM_GLBL, intm_glbl.bytes)) {
    LOGE("Failed to read global interrupt mask register");
    return;
  }

  max77734_reg_int_m_chg_t int_m_chg = {.bytes[0] = 0xff};
  if (!read_register(MAX77734_REG_INT_M_CHG, int_m_chg.bytes)) {
    LOGE("Failed to read charge interrupt mask register");
    return;
  }

  // Unmask thermal alarm interrupts in global interrupt mask
  intm_glbl.values.TJAL1_RM = 0;  // Enable TJAL1 interrupt
  intm_glbl.values.TJAL2_RM = 0;  // Enable TJAL2 interrupt
  if (!write_register(MAX77734_REG_INTM_GLBL, intm_glbl.bytes)) {
    LOGE("Failed to enable thermal alarm interrupts (INTM_GLBL)");
    return;
  }

  // Unmask junction temperature regulation interrupt in charge interrupt mask
  int_m_chg.values.TJ_REG_M = 0;  // Enable TJ_REG interrupt
  if (!write_register(MAX77734_REG_INT_M_CHG, int_m_chg.bytes)) {
    LOGE("Failed to enable TJ_REG interrupt (INT_M_CHG)");
    return;
  }

  // Clear any pending thermal interrupts (read to clear)
  max77734_reg_int_glbl_t int_glbl = {0};
  read_register(MAX77734_REG_INT_GLBL, int_glbl.bytes);

  max77734_reg_int_chg_t int_chg = {0};
  read_register(MAX77734_REG_INT_CHG, int_chg.bytes);
}

bool max77734_check_thermal_status(bool* tjal1, bool* tjal2, bool* tj_reg) {
  if (!tjal1 || !tjal2 || !tj_reg) {
    return false;
  }

  *tjal1 = false;
  *tjal2 = false;
  *tj_reg = false;

  // Read interrupt registers
  max77734_reg_int_glbl_t int_glbl = {0};
  if (!read_register(MAX77734_REG_INT_GLBL, int_glbl.bytes)) {
    LOGE("Failed to read INT_GLBL");
    return false;
  }

  max77734_reg_int_chg_t int_chg = {0};
  if (!read_register(MAX77734_REG_INT_CHG, int_chg.bytes)) {
    LOGE("Failed to read INT_CHG");
    return false;
  }

  // Check thermal alarm rising edge interrupts
  *tjal1 = int_glbl.values.TJAL1_R;
  *tjal2 = int_glbl.values.TJAL2_R;

  // Check junction temperature regulation interrupt
  *tj_reg = int_chg.values.TJ_REG_I;

  // Return true if any thermal event occurred
  return (*tjal1 || *tjal2 || *tj_reg);
}

uint8_t max77734_get_register_count(void) {
  return 27;
}

void max77734_read_register(uint8_t index, uint8_t* offset_out, uint8_t* value_out) {
  ASSERT(offset_out != NULL);
  ASSERT(value_out != NULL);

  switch (index) {
    case 0:
      *offset_out = MAX77734_REG_INT_GLBL;
      read_register(MAX77734_REG_INT_GLBL, value_out);
      break;

    case 1:
      *offset_out = MAX77734_REG_INT_CHG;
      read_register(MAX77734_REG_INT_CHG, value_out);
      break;

    case 2:
      *offset_out = MAX77734_REG_STAT_CHG_A;
      read_register(MAX77734_REG_STAT_CHG_A, value_out);
      break;

    case 3:
      *offset_out = MAX77734_REG_STAT_CHG_B;
      read_register(MAX77734_REG_STAT_CHG_B, value_out);
      break;

    case 4:
      *offset_out = MAX77734_REG_ERCFLAG;
      read_register(MAX77734_REG_ERCFLAG, value_out);
      break;

    case 5:
      *offset_out = MAX77734_REG_STAT_GLBL;
      read_register(MAX77734_REG_STAT_GLBL, value_out);
      break;

    case 6:
      *offset_out = MAX77734_REG_INTM_GLBL;
      read_register(MAX77734_REG_INTM_GLBL, value_out);
      break;

    case 7:
      *offset_out = MAX77734_REG_INT_M_CHG;
      read_register(MAX77734_REG_INT_M_CHG, value_out);
      break;

    case 8:
      *offset_out = MAX77734_REG_CNFG_GLBL;
      read_register(MAX77734_REG_CNFG_GLBL, value_out);
      break;

    case 9:
      *offset_out = MAX77734_REG_CID;
      read_register(MAX77734_REG_CID, value_out);
      break;

    case 10:
      *offset_out = MAX77734_REG_CNFG_WDT;
      read_register(MAX77734_REG_CNFG_WDT, value_out);
      break;

    case 11:
      *offset_out = MAX77734_REG_CNFG_CHG_A;
      read_register(MAX77734_REG_CNFG_CHG_A, value_out);
      break;

    case 12:
      *offset_out = MAX77734_REG_CNFG_CHG_B;
      read_register(MAX77734_REG_CNFG_CHG_B, value_out);
      break;

    case 13:
      *offset_out = MAX77734_REG_CNFG_CHG_C;
      read_register(MAX77734_REG_CNFG_CHG_C, value_out);
      break;

    case 14:
      *offset_out = MAX77734_REG_CNFG_CHG_D;
      read_register(MAX77734_REG_CNFG_CHG_D, value_out);
      break;

    case 15:
      *offset_out = MAX77734_REG_CNFG_CHG_E;
      read_register(MAX77734_REG_CNFG_CHG_E, value_out);
      break;

    case 16:
      *offset_out = MAX77734_REG_CNFG_CHG_F;
      read_register(MAX77734_REG_CNFG_CHG_F, value_out);
      break;

    case 17:
      *offset_out = MAX77734_REG_CNFG_CHG_G;
      read_register(MAX77734_REG_CNFG_CHG_G, value_out);
      break;

    case 18:
      *offset_out = MAX77734_REG_CNFG_CHG_H;
      read_register(MAX77734_REG_CNFG_CHG_H, value_out);
      break;

    case 19:
      *offset_out = MAX77734_REG_CNFG_CHG_I;
      read_register(MAX77734_REG_CNFG_CHG_I, value_out);
      break;

    case 20:
      *offset_out = MAX77734_REG_CNFG_LDO_A;
      read_register(MAX77734_REG_CNFG_LDO_A, value_out);
      break;

    case 21:
      *offset_out = MAX77734_REG_CNFG_LDO_B;
      read_register(MAX77734_REG_CNFG_LDO_B, value_out);
      break;

    case 22:
      *offset_out = MAX77734_REG_CNFG_SNK1_A;
      read_register(MAX77734_REG_CNFG_SNK1_A, value_out);
      break;

    case 23:
      *offset_out = MAX77734_REG_CNFG_SNK1_B;
      read_register(MAX77734_REG_CNFG_SNK1_B, value_out);
      break;

    case 24:
      *offset_out = MAX77734_REG_CNFG_SNK2_A;
      read_register(MAX77734_REG_CNFG_SNK2_A, value_out);
      break;

    case 25:
      *offset_out = MAX77734_REG_CNFG_SNK2_B;
      read_register(MAX77734_REG_CNFG_SNK2_B, value_out);
      break;

    case 26:
      *offset_out = MAX77734_REG_CNFG_SNK_TOP;
      read_register(MAX77734_REG_CNFG_SNK_TOP, value_out);
      break;

    default:
      *offset_out = 0;
      *value_out = 0;
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
