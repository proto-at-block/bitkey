#include "mpu_regions.h"

// MemManage_Handler is provided by the Memfault SDK (resolved via the
// MEMFAULT_EXC_HANDLER_MEMORY_MANAGEMENT override in
// firmware/hal/memfault/inc/memfault_platform_config.h). Memfault's NAKED
// handler captures the faulting exception frame, the SCB fault status
// registers (CFSR / MMFAR / BFAR / HFSR / SHCSR), and the active stack into
// a coredump tagged with reboot reason `kMfltRebootReason_MemFault`, then
// reboots via `memfault_platform_reboot()`.

__attribute__((weak)) void mpu_regions_init(void) {}
