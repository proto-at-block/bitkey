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

#define MAX17262_REG_PERCENT_IN_MILLIPERCENT(reg_percent) \
  (uint32_t)(((uint32_t)reg_percent * 1000) / 256)
#define MAX17262_REG_VOLTS_IN_MV(reg_volts) (uint32_t)(((uint32_t)reg_volts * 125) / 1600)

// Each unit reported from the FG is 156.25μA.
// To convert to milliamps, we need to multiply by 156.25 and divide by 1000000.
// This is the same as multiplying by 5 and dividing by 32.
#define MAX17262_REG_CURRENT_UNIT_AS_MILLIAMPS(reg_amps) (int32_t)((reg_amps * (5.0)) / 32.0)
