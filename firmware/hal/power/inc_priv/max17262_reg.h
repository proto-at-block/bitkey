#pragma once

#include "attributes.h"

#include <stdbool.h>
#include <stdint.h>

#define BATTERY_MAX17262H_RSENSE             30 /* m-ohm */
#define MAX17262H_DESIGNCAP_REG(bat_cap_mah) (bat_cap_mah * BATTERY_MAX17262H_RSENSE / 5)
#define MAX17262H_ICHGTERM_REG(term_cur_ma)  (((term_cur_ma * BATTERY_MAX17262H_RSENSE) << 4) / 25)
#define MAX17055_VEMPTY_REG(ve_mv, vr_mv)    (((ve_mv / 10) << 7) | (vr_mv / 40))

typedef enum {
  MAX17262_REG_STATUS = 0x00,
  MAX17262_REG_REPCAP = 0x05,
  MAX17262_REG_REPSOC = 0x06,
  MAX17262_REG_VCELL = 0x09,
  MAX17262_REG_FULLCAPREP = 0x10,
  MAX17262_REG_AVGCURRENT = 0x0B,
  MAX17262_REG_MIXCAP = 0x0F,
  MAX17262_REG_QRTABLE00 = 0x12,
  MAX17262_REG_CYCLES = 0x17,
  MAX17262_REG_DESIGNCAP = 0x18,
  MAX17262_REG_ICHGTERM = 0x1E,
  MAX17262_REG_AVGCAP = 0x1F,
  MAX17262_REG_DEVNAME = 0x21,
  MAX17262_REG_QRTABLE10 = 0x22,
  MAX17262_REG_FULLCAPNOM = 0x23,
  MAX17262_REG_LEARNCFG = 0x28,
  MAX17262_REG_RELAXCFG = 0x2A,
  MAX17262_REG_MISCCFG = 0x2B,
  MAX17262_REG_QRTABLE20 = 0x32,
  MAX17262_REG_RCOMP0 = 0x38,
  MAX17262_REG_TEMPCO = 0x39,
  MAX17262_REG_VEMPTY = 0x3A,
  MAX17262_REG_FSTAT = 0x3D,
  MAX17262_REG_QRTABLE30 = 0x42,
  MAX17262_REG_DQACC = 0x45,
  MAX17262_REG_DPACC = 0x46,
  MAX17262_REG_COMMAND = 0x60,
  MAX17262_REG_UNLOCK_MODEL_STEP_1 = 0x62,
  MAX17262_REG_UNLOCK_MODEL_STEP_2 = 0x63,
  MAX17262_OCVTABLE0 = 0x80,
  MAX17262_XTABLE0 = 0x90,
  MAX17262_REG_HIBCFG = 0xBA,
  MAX17262_REG_CONFIG2 = 0xBB,
  MAX17262_REG_MODELCFG = 0xDB,
  MAX17262_VFSOC = 0xFF,
} max17262_reg_t;

typedef enum {
  MAX17262_DEVNAME = 0x4039,
} max17262_devname_t;

#define MAX17262_REG_CONFIG2_LDMDL_MASK 0x0020

typedef union {
  uint8_t bytes[2];
  struct PACKED {
    bool unused_1 : 1;  // Unused
    bool POR : 1;       // Power-On Reset
    bool Imn : 1;       // Minimum Current Alert Threshold Exceeded
    bool Bst : 1;       // Battery Status
    bool unused_2 : 1;  // Unused
    bool unused_3 : 1;  // Unused
    bool Imx : 1;       // Maximum Current Alert Threshold Exceeded
    bool dSOCi : 1;     // State of Charge 1% Change Alert
    bool Vmn : 1;       // Minimum Voltage Alert Threshold Exceeded
    bool Tmn : 1;       // Minimum Temperature Alert Threshold Exceeded
    bool Smn : 1;       // Minimum SOC Alert Threshold Exceeded
    bool Bi : 1;        // Battery Insertion
    bool Vmx : 1;       // Maximum Voltage Alert Threshold Exceeded
    bool Tmx : 1;       // Maximum Temperature Alert Threshold Exceeded
    bool Smx : 1;       // Maximum SOC Alert Threshold Exceeded
    bool Br : 1;        // Battery Removal
  } values;
} max17262_reg_status_t;

typedef union {
  uint8_t bytes[2];
  struct PACKED {
    bool DNR : 1;           // Data Not Ready
    uint16_t unused_1 : 5;  // Unused
    bool RelDt2 : 1;        // Long Relaxation
    bool FQ : 1;            // Full Qualified
    bool EDet : 1;          // Empty Detection
    bool RelDt : 1;         // Relaxed Cell Detection
    uint16_t unused_2 : 6;
  } values;
} max17262_reg_fstat_t;

typedef union {
  uint8_t bytes[2];
  struct PACKED {
    uint8_t VR : 7;   // Recovery Voltage
    uint16_t VE : 9;  // Empty Voltage Target, during load
  } values;
} max17262_reg_vempty_t;

typedef union {
  uint8_t bytes[2];
  struct PACKED {
    bool zeros_1 : 1;      // Zero
    bool zeros_2 : 1;      // Zero
    uint8_t reserved : 2;  // Reserved
    uint8_t ModelID : 4;   // Lithium model to use
    bool zeros_3 : 1;      // Zero
    bool zeros_4 : 1;      // Zero
    bool VChg : 1;         // Set VChg to 1 for a charge voltage higher than 4.25V (4.3V–4.4V)
    bool zeros_5 : 1;      // Zero
    bool zeros_6 : 1;      // Zero
    bool R100 : 1;         // If using 100kΩ NTC, set R100 = 1; if using 10kΩ NTC, set R100 = 0
    bool zeros_7 : 1;      // Zero
    bool refresh : 1;      // Set Refresh to 1 to command the model reload
  } values;
} max17262_reg_modelcfg_t;
