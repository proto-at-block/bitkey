#pragma once

#include "mcu_i2c.h"

#include "em_device.h"
#include "em_i2c.h"

#define I2C_IF_ERRORS  (I2C_IF_BUSERR | I2C_IF_ARBLOST)
#define I2C_IEN_ERRORS (I2C_IEN_BUSERR | I2C_IEN_ARBLOST)

typedef I2C_TypeDef mcu_i2c_bus_t;

typedef struct {
  /* RTOS */
  rtos_mutex_t init_access;
  rtos_mutex_t transfer_access;
  rtos_mutex_t ctitical_access;

  /* Performance Counters */
  struct {
    perf_counter_t* transfers;
    perf_counter_t* errors;
  } perf;
} mcu_i2c_bus_state_t;

void _mcu_i2c_init(void);
mcu_i2c_bus_t* _mcu_i2c_handle(const mcu_i2c_t peripheral);
void _mcu_i2c_int_clear(mcu_i2c_bus_t* bus, const uint32_t flags);
bool _mcu_i2c_transfer_init(mcu_i2c_bus_t* bus);

mcu_i2c_bus_state_t* _mcu_i2c_bus_get_state(const mcu_i2c_t peripheral);
void _mcu_i2c_bus_init_state(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_lock(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_unlock(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_ctitical_lock(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_critical_unlock(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_init_lock(const mcu_i2c_t peripheral);
bool _mcu_i2c_bus_init_unlock(const mcu_i2c_t peripheral);
