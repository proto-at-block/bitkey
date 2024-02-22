#pragma once

#include "max17262_modelgauge.h"

typedef enum {
  BATTERY_VARIANT_DEFAULT = 0,
  BATTERY_VARIANT_R = 1,
  BATTERY_VARIANT_E = 2,
  BATTERY_VARIANT_MAX,
} battery_variant_t
  __attribute__((
    aligned(sizeof(uint32_t))));  // Prevent enum packing which conflicts with protobuf variant enum

max17262_modelgauge_t* battery_config_get(const battery_variant_t variant);
