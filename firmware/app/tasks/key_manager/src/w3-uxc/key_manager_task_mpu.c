#include "mpu_auto.h"

DECLARE_TASK_MPU(key_manager);

void key_manager_task_mpu_init(void) {
  /* Mirrors Core for Crypto Access */
  _key_manager_thread_regions.privilege = rtos_thread_privileged_bit;
}
