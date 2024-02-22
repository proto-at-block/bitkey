#pragma once

#include <stdbool.h>

typedef struct {
  bool bl_upgrade;
} fwup_task_options_t;

void fwup_task_create(fwup_task_options_t options);
