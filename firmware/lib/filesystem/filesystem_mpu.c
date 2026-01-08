#include "mpu_auto.h"

rtos_thread_mpu_t _fs_mount_task_regions = {0};

void fs_mount_task_mpu_init(void) {
  _fs_mount_task_regions.privilege = rtos_thread_privileged_bit;
}
