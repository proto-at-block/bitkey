#include "mpu_auto.h"

DECLARE_TASK_MPU(mfgtest);

void mfgtest_mpu_init(void) {
  _mfgtest_thread_regions.privilege = rtos_thread_privileged_bit;
}
