#include "mpu_auto.h"

DECLARE_TASK_MPU(sysinfo);

void sysinfo_task_mpu_init(void) {
  _sysinfo_thread_regions.privilege = rtos_thread_privileged_bit;
}
