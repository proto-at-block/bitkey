#pragma once

#include "attributes.h"

#include <stdbool.h>
#include <stdint.h>

typedef enum {
  /* Global config */
  MAX77734_REG_INT_GLBL = 0x00,
  MAX77734_REG_INT_CHG,
  MAX77734_REG_STAT_CHG_A,
  MAX77734_REG_STAT_CHG_B,
  MAX77734_REG_ERCFLAG,
  MAX77734_REG_STAT_GLBL,
  MAX77734_REG_INTM_GLBL,
  MAX77734_REG_INT_M_CHG,
  MAX77734_REG_CNFG_GLBL,
  MAX77734_REG_CID,
  MAX77734_REG_CNFG_WDT,

  /* Charger config */
  MAX77734_REG_CNFG_CHG_A = 0x20,
  MAX77734_REG_CNFG_CHG_B,
  MAX77734_REG_CNFG_CHG_C,
  MAX77734_REG_CNFG_CHG_D,
  MAX77734_REG_CNFG_CHG_E,
  MAX77734_REG_CNFG_CHG_F,
  MAX77734_REG_CNFG_CHG_G,
  MAX77734_REG_CNFG_CHG_H,
  MAX77734_REG_CNFG_CHG_I,

  /* LDO config */
  MAX77734_REG_CNFG_LDO_A = 0x30,
  MAX77734_REG_CNFG_LDO_B,

  /* Current sinks config */
  MAX77734_REG_CNFG_SNK1_A = 0x40,
  MAX77734_REG_CNFG_SNK1_B,
  MAX77734_REG_CNFG_SNK2_A,
  MAX77734_REG_CNFG_SNK2_B,
  MAX77734_REG_CNFG_SNK_TOP,
} max77734_reg_t;

typedef enum {
  MAX77734_CHG_DTLS_OFF = 0b0000,
  MAX77734_CHG_DTLS_PREQUALIFICATION = 0b0001,
  MAX77734_CHG_DTLS_CC = 0b0010,
  MAX77734_CHG_DTLS_JEITA_CC = 0b0011,
  MAX77734_CHG_DTLS_CV = 0b0100,
  MAX77734_CHG_DTLS_JEITA_CV = 0b0101,
  MAX77734_CHG_DTLS_TOP_OFF = 0b0110,
  MAX77734_CHG_DTLS_JEITA_TOP_OFF = 0b0111,
  MAX77734_CHG_DTLS_DONE = 0b1000,
  MAX77734_CHG_DTLS_JEITA_DONE = 0b1001,
  MAX77734_CHG_DTLS_PREQUALIFICATION_TIMER_FAULT = 0b1010,
  MAX77734_CHG_DTLS_FAST_CHARGE_TIMER_FAULT = 0b1011,
  MAX77734_CHG_DTLS_BATTERY_TEMP_FAULT = 0b1100,
} max77734_chg_dtls_bitfield_t;

typedef enum {
  MAX77734BENP = 0x01,
  MAX77734CENP = 0x03,
  MAX77734GENP = 0x04,
  MAX77734QENP = 0x07,
} max77734_reg_cid_values_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool RSVD_1 : 1;    // Reserved
    bool RSVD_2 : 1;    // Reserved
    bool NENLDO_F : 1;  // nENLDO Falling Interrupt
    bool NENLDO_R : 1;  // nENLDO Rising Interrupt
    bool TJAL1_R : 1;   // Thermal Alarm 1 Rising Interrupt
    bool TJAL2_R : 1;   // Thermal Alarm 2 Rising Interrupt
    bool POKLDO : 1;    // POKLDO Interrupt
    bool RSVD_3 : 1;    // Reserved
  } values;
} max77734_reg_int_glbl_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool THM_I : 1;         // Thermistor Related Interrupt
    bool CHG_I : 1;         // Charger Related Interrupt
    bool CHGIN_I : 1;       // CHGIN Related Interrupt
    bool TJ_REG_I : 1;      // Die Junction Temperature Regulation Interrupt
    bool CHGIN_CTRL_I : 1;  // CHGIN Control-Loop Related Interrupt
    bool SYS_CTRL_I : 1;    // Minimum System Voltage Regulation Loop Related Interrupt
    bool SYS_CNFG_I : 1;    // System Voltage Configuration Error Interrupt
    bool RSVD : 1;          // Reserved
  } values;
} max77734_reg_int_chg_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t CID : 4;  // Chip Identification Code for OTP Options
    uint8_t RSVD;     // Reserved
  } values;
} max77734_reg_cid_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t THM_COLD : 2;  // Sets the VCOLD JEITA Temperature Threshold
    uint8_t THM_COOL : 2;  // Sets the VCOOL JEITA Temperature Threshold
    uint8_t THM_WARM : 2;  // Sets the VWARM JEITA Temperature Threshold
    uint8_t THM_HOT : 2;   // Sets the VHOT JEITA Temperature Threshold
  } values;
} max77734_reg_cnfg_chg_a_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool CHG_EN : 1;         // Charger enable
    bool I_PQ : 1;           // Prequalification charge current
    uint8_t ICHGIN_LIM : 3;  // CHGIN Input Current Limit
    uint8_t VCHGIN_MIN : 3;  // Minimum CHGIN Regulation Voltage
  } values;
} max77734_reg_cnfg_chg_b_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t T_TOPOFF : 3;  // PrequalificationVoltage Threshold
    uint8_t I_TERM : 2;    // Charge Termination Current
    uint8_t CHG_PQ : 3;    // Prequalification Voltage Threshold
  } values;
} max77734_reg_cnfg_chg_c_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t VSYS_REG : 5;  // System Voltage Regulation Point
    uint8_t TJ_REG : 3;    //  Die Junction Temperature Regulatoin Point
  } values;
} max77734_reg_cnfg_chg_d_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t T_FAST_CHG : 2;  // Fast-Charge Safety Timer
    uint8_t CHG_CC : 6;      // Fast-Charge Constant Current Value
  } values;
} max77734_reg_cnfg_chg_e_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool RSVD : 1;             // Reserved
    bool THM_EN : 1;           // Thermistor Enable
    uint8_t CHG_CC_JEITA : 6;  // Sets IFAST-CHG_JEITA
  } values;
} max77734_reg_cnfg_chg_f_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool RSVD : 1;       // Reserved
    bool USBS : 1;       // Setting this bit places CHGIN in USB suspend mode
    uint8_t CHG_CV : 6;  // Fast-charge battery regulation voltage
  } values;
} max77734_reg_cnfg_chg_g_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool RSVD1 : 1;            // Reserved
    bool RSVD2 : 1;            // Reserved
    uint8_t CHG_CV_JEITA : 6;  // Sets VFAST-CHG_JEITA
  } values;
} max77734_reg_cnfg_chg_h_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t MUX_SEL : 4;            // Analog channel to connect to AMUX
    uint8_t IMON_DISCHG_SCALE : 4;  // Battery discharge current full-scale current value
  } values;
} max77734_reg_cnfg_chg_i_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t LDO_VREG : 7;  // LDO Target Regulation Voltage
    bool ADE_LDO : 1;      // LDO Active Discharge Resistor Control
  } values;
} max77734_reg_cnfg_ldo_a_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t LDO_EN : 2;  // LDO Enable Control
    uint8_t LDO_PM : 2;  // LDO Power Mode Control
    uint8_t RSVD;        // Reserved
  } values;
} max77734_reg_cnfg_ldo_b_t;

/**
 * @brief LDO Enable Control (LDO_EN) values for CNFG_LDO_B register.
 */
typedef enum {
  MAX77734_LDO_EN_AUTO = 0b00,      /**< LDO enables when nENLDO asserts or CHGIN valid */
  MAX77734_LDO_EN_FORCED_ON = 0b01, /**< LDO is forced ON */
} max77734_ldo_en_t;

/**
 * @brief LDO Power Mode Control (LDO_PM) values for CNFG_LDO_B register.
 */
typedef enum {
  MAX77734_LDO_PM_LOW_POWER = 0b00, /**< Forced low-power mode */
  MAX77734_LDO_PM_NORMAL = 0b01,    /**< Normal mode */
} max77734_ldo_pm_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t THM_DTLS : 3;      // Battery Temperature Details
    bool TJ_REG_STAT : 1;      // Maximum Junction Temperature Regulation Loop Status
    bool VSYS_MIN_STAT : 1;    // Minimum System Voltage Regulation Loop Status
    bool ICHGIN_LIM_STAT : 1;  // Input Current-Limit Loop Status
    bool VCHGIN_MIN_STAT : 1;  // Minimum Input Voltage Regulation Loop Status
    bool RSDV : 1;             // Reserved
  } values;
} max77734_reg_stat_chg_a_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t TIME_SUS : 1;    // Time Suspend Indicator
    uint8_t CHG : 1;         // Quick Charger Status
    uint8_t CHGIN_DTLS : 2;  // CHGIN Status Details
    uint8_t CHG_DTLS : 4;    // Indicates the current state of the charge
  } values;
} max77734_reg_stat_chg_b_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t STAT_IRQ : 1;    // Interrupt Status
    uint8_t BOK : 1;         // System Bias OK Status Bit
    uint8_t STAT_ENLDO : 1;  // Debounced Status for the nENLDO input
    uint8_t TJAL1_S : 1;     // Thermal Alarm 1 Status
    uint8_t TJAL2_S : 1;     // Thermal Alarm 2 Status
    uint8_t POKLDO_S : 1;    // LDO Power-OK Status
    uint8_t DIDM : 2;        // Device Identification Bits for Metal Options
  } values;
} max77734_reg_stat_glbl_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool RSVD_1 : 1;     // Reserved
    bool RSVD_2 : 1;     // Reserved
    bool NENLDO_FM : 1;  // nENLDO Falling Interrupt Mask
    bool NENLDO_RM : 1;  // nENLDO Rising Interrupt Mask
    bool TJAL1_RM : 1;   // Thermal Alarm 1 Rising Interrupt Mask
    bool TJAL2_RM : 1;   // Thermal Alarm 2 Rising Interrupt Mask
    bool POKLDOM : 1;    // POKLDO Interrupt Mask
    bool RSVD_3 : 1;     // Reserved
  } values;
} max77734_reg_intm_glbl_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    uint8_t SFT_CTRL : 2;  // Software Control Functions
    bool DB_NENLDO : 1;    // Debounce Timer for the nENLDO pin
    bool NENLDO_MODE : 1;  // nENLDO (ONKEY) Default Configuration Mode
    bool BIAS_REQ : 1;     // System Bias Enable Software Request
    bool BIAS_LPM : 1;     // System Bias Low-Power Mode Software Request
    bool T_MRST : 1;       // Sets the Manual Reset Time
    bool PU_DIS : 1;       // nENLDO Internal Pullup Resistor Control to VCCINT
  } values;
} max77734_reg_cnfg_glbl_t;

typedef union {
  uint8_t bytes[1];
  struct PACKED {
    bool THM_M : 1;
    bool CHG_M : 1;
    bool CHGIN_M : 1;
    bool TJ_REG_M : 1;
    bool CHGIN_CTRL_M : 1;
    bool SYS_CTRL_M : 1;
    bool SYS_CNFG_M : 1;
    bool RSVD : 1;
  } values;
} max77734_reg_int_m_chg_t;
