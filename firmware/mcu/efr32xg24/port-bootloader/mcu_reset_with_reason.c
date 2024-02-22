#include "mcu_reset_impl.h"

void mcu_reset_with_reason(mcu_reset_reason_t reason) {
  __mcu_reset_with_reason(reason);
}
