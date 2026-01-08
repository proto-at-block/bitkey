#include "mcu_systick.h"

#include "attributes.h"
#include "mcu.h"
#include "rtos_mpu.h"

SYSCALL uint32_t mcu_systick_get_reload(void) {
  uint32_t ret_val;
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }

  ret_val = SysTick->LOAD & SysTick_LOAD_RELOAD_Msk;

  if (!was_priv) {
    rtos_thread_reset_privilege();
  }

  return ret_val;
}

SYSCALL uint32_t mcu_systick_get_value(void) {
  uint32_t ret_val;
  int was_priv = rtos_thread_is_privileged();
  if (!was_priv) {
    rtos_thread_raise_privilege();
  }

  ret_val = SysTick->VAL & SysTick_VAL_CURRENT_Msk;

  if (!was_priv) {
    rtos_thread_reset_privilege();
  }

  return ret_val;
}
