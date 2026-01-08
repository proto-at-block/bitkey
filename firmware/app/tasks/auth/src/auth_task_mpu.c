#include "mpu_auto.h"

DECLARE_TASK_MPU(auth_main);
DECLARE_TASK_MPU(auth_matching);

void auth_task_mpu_init(void) {
  _auth_main_thread_regions.privilege = rtos_thread_privileged_bit;
  _auth_matching_thread_regions.privilege = rtos_thread_privileged_bit;
}
