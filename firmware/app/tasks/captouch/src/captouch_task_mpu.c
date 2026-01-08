#include "mpu_auto.h"

DECLARE_TASK_MPU(captouch);

void captouch_task_mpu_init(void) {
  _captouch_thread_regions.privilege = rtos_thread_privileged_bit;
}
