#pragma once

#include "secure_engine.h"

#include <stdbool.h>

typedef struct {
  sl_se_status_t se_status;
  uint32_t reset_reason;
} tamper_cause_t;

void tamper_init(void);
