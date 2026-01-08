#include "mpu_auto.h"

DECLARE_TASK_MPU(shell);

void shell_task_mpu_init(void) {
  _shell_thread_regions.privilege = rtos_thread_privileged_bit;
}
