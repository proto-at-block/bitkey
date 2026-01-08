#include "fwup.h"
#include "mpu_auto.h"

DECLARE_TASK_MPU(key_manager);
DECLARE_TASK_MPU(crypto);

void key_manager_mpu_init(void) {
  _key_manager_thread_regions.privilege = rtos_thread_privileged_bit;
  _crypto_thread_regions.privilege = rtos_thread_privileged_bit;

  mpu_setup_privileged_default(_key_manager_thread_regions.regions);
  mpu_setup_privileged_default(_crypto_thread_regions.regions);
}
