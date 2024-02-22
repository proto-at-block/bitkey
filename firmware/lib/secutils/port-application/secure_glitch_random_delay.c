#include "rtos_mpu.h"
#include "secutils.h"
#include "secutils_impl.h"

SYSCALL void secure_glitch_random_delay(void) {
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }

  __secure_glitch_random_delay();

  if (!was_priv) {
    rtos_thread_reset_privilege();
  }
}
