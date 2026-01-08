#include "attributes.h"
#include "memfault/ports/reboot_reason.h"

#include <stdint.h>

eMemfaultRebootReason memfault_translate_rmu_cause_to_memfault_enum(uint32_t UNUSED(reset_cause)) {
  return kMfltRebootReason_Unknown;
}
