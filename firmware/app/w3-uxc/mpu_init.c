#include "mpu_regions.h"

extern void display_task_mpu_init(void);
extern void display_send_task_mpu_init(void);
extern void fs_mount_task_mpu_init(void);
extern void fwup_task_mpu_init(void);
extern void key_manager_task_mpu_init(void);
extern void mfgtest_mpu_init(void);
extern void shell_task_mpu_init(void);
extern void sysinfo_task_mpu_init(void);
extern void touch_task_mpu_init(void);
extern void usart_task_mpu_init(void);

void mpu_regions_init(void) {
  display_task_mpu_init();
  display_send_task_mpu_init();
  fs_mount_task_mpu_init();
  fwup_task_mpu_init();
  key_manager_task_mpu_init();
  mfgtest_mpu_init();
#ifndef CONFIG_PROD
  shell_task_mpu_init();
#endif
  sysinfo_task_mpu_init();
  touch_task_mpu_init();
  usart_task_mpu_init();
}
