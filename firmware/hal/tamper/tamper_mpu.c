#include "mpu_auto.h"

DECLARE_TASK_MPU(tamper);

void tamper_mpu_init(void) {
  _tamper_thread_regions.privilege = rtos_thread_privileged_bit;
}
