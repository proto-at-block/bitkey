#include "attributes.h"
#include "mcu_reset_impl.h"
#include "rtos_mpu.h"
#include "rtos_thread.h"

SYSCALL NO_RETURN void mcu_reset_with_reason(mcu_reset_reason_t reason) {
  if (!rtos_in_isr()) {
    rtos_thread_reset_privilege();
    rtos_thread_raise_privilege();
  }
  __mcu_reset_with_reason(reason);
  // we wont hit this code path since __mcu_reset_with_reason does not return, but good hygiene
  rtos_thread_reset_privilege();
}
