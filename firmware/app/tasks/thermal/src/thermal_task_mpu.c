#include "mpu_auto.h"
#include "rtos_mpu.h"

DECLARE_TASK_MPU(thermal);

void thermal_task_mpu_init(void) {
  _thermal_thread_regions.privilege = rtos_thread_privileged_bit;
}
