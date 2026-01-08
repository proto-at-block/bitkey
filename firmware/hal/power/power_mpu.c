#include "mpu_auto.h"

DECLARE_TASK_MPU(charger);
DECLARE_TASK_MPU(fuel_gauge);

void power_mpu_init(void) {
  _charger_thread_regions.privilege = rtos_thread_privileged_bit;
  _fuel_gauge_thread_regions.privilege = rtos_thread_privileged_bit;
}
