#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef struct {
  uint32_t soc;
  uint32_t vcell;
  int32_t avg_current;
  uint32_t cycles;
} max17262_regdump_t;

typedef enum {
  MAX17262_STATUS_ERR,
  MAX17262_STATUS_UNINITIALISED,
  MAX17262_STATUS_FAILED,
  MAX17262_STATUS_POWER_ON_RESET,
  MAX17262_STATUS_MODELGAUGE_UNINITIALISED,
  MAX17262_STATUS_OK,
} max17262_status_t;

max17262_status_t max17262_init(void);
bool max17262_validate(void);
uint32_t max17262_soc_millipercent(void);
uint32_t max17262_vcell_mv(void);
bool max17262_por_initialise(void);
int32_t max17262_average_current(void);
bool max17262_clear_modelgauge(void);

void max17262_get_regdump(max17262_regdump_t* registers_out);

// Configure SOC alerts for 1% change detection
// @param enable_1percent_change - Enable interrupt on every 1% SOC change
bool max17262_configure_soc_alerts(bool enable_1percent_change);

// Set SOC threshold values for alerts
// @param min_soc - Minimum SOC threshold (0-100%), 0 to disable
// @param max_soc - Maximum SOC threshold (0-100%), 255 to disable
bool max17262_set_soc_thresholds(uint8_t min_soc, uint8_t max_soc);

// Enable/disable alert output on ALRT pin
bool max17262_enable_alerts(bool enable);

// Clear all alert status flags
bool max17262_clear_alerts(void);

// SOC alert types we actually monitor
typedef enum {
  MAX17262_SOC_ALERT_NONE = 0,
  MAX17262_SOC_ALERT_1PCT_CHANGE,    // SOC changed by 1%
  MAX17262_SOC_ALERT_MIN_THRESHOLD,  // SOC dropped below minimum threshold
  MAX17262_SOC_ALERT_MAX_THRESHOLD,  // SOC rose above maximum threshold
} max17262_soc_alert_t;

// Get SOC alert type
bool max17262_get_soc_alert(max17262_soc_alert_t* alert);

#define MAX17262_REG_PERCENT_IN_MILLIPERCENT(reg_percent) \
  (uint32_t)(((uint32_t)reg_percent * 1000) / 256)
#define MAX17262_REG_VOLTS_IN_MV(reg_volts) (uint32_t)(((uint32_t)reg_volts * 125) / 1600)

// Each unit reported from the FG is 156.25μA.
// To convert to milliamps, we need to multiply by 156.25 and divide by 1000000.
// This is the same as multiplying by 5 and dividing by 32.
#define MAX17262_REG_CURRENT_UNIT_AS_MILLIAMPS(reg_amps) (int32_t)((reg_amps * (5.0)) / 32.0)
