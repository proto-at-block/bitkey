#include "mpu_regions.h"

extern void ui_task_mpu_init(void);
extern void fwup_task_mpu_init(void);
extern void nfc_task_mpu_init(void);
extern void auth_task_mpu_init(void);
extern void key_manager_mpu_init(void);
extern void shell_task_mpu_init(void);
extern void sysinfo_task_mpu_init(void);
extern void power_mpu_init(void);
extern void tamper_mpu_init(void);
extern void fs_mount_task_mpu_init(void);
extern void captouch_task_mpu_init(void);
extern void mfgtest_mpu_init(void);
extern void usart_task_mpu_init(void);
extern void thermal_task_mpu_init(void);

void mpu_regions_init(void) {
  ui_task_mpu_init();
  fwup_task_mpu_init();
  nfc_task_mpu_init();
  auth_task_mpu_init();
  key_manager_mpu_init();
  sysinfo_task_mpu_init();
  power_mpu_init();
  tamper_mpu_init();
  fs_mount_task_mpu_init();
  captouch_task_mpu_init();
  mfgtest_mpu_init();
  usart_task_mpu_init();
  thermal_task_mpu_init();

#ifndef CONFIG_PROD
  shell_task_mpu_init();
#endif
}
