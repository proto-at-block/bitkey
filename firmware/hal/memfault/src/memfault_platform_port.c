#include "fatal.h"
#include "filesystem.h"
#include "log.h"
#include "mcu_reset.h"
#include "memfault/components.h"
#include "memfault/core/data_packetizer_source.h"
#include "memfault/core/event_storage.h"
#include "memfault/core/log.h"
#include "memfault/core/math.h"
#include "memfault/core/platform/debug_log.h"
#include "memfault/core/reboot_tracking.h"
#include "memfault/panics/arch/arm/cortex_m.h"
#include "memfault/panics/fault_handling.h"
#include "memfault/panics/platform/coredump.h"
#include "memfault/ports/freertos.h"
#include "memfault/ports/freertos_coredump.h"
#include "memfault/ports/reboot_reason.h"
#include "memfault_platform_translate.h"
#include "memfault_reboot_tracking_private.h"
#include "sysinfo.h"
#include "telemetry_storage.h"

#include <limits.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>

#if MEMFAULT_ENABLE_REBOOT_DIAG_DUMP
#define MEMFAULT_PRINT_RESET_INFO(...) MEMFAULT_LOG_INFO(__VA_ARGS__)
#else
#define MEMFAULT_PRINT_RESET_INFO(...)
#endif

extern char ram_addr[];
extern char ram_size[];
extern char __privileged_functions_start__[];
extern char __unprivileged_flash_end__[];

static void platform_coredump_init(void);

MEMFAULT_PUT_IN_SECTION(".noinit.mflt_reboot_tracking")
static uint8_t s_reboot_tracking[MEMFAULT_REBOOT_TRACKING_REGION_SIZE];

// A counter keeps coredump capture disabled until all overlapping sensitive operations finish.
_Static_assert(ATOMIC_INT_LOCK_FREE == 2,
               "The coredump sensitivity guard must be lock-free in fault context");
static atomic_uint s_sensitive_operation_depth;

void memfault_port_coredump_sensitive_operation_begin(void) {
  const unsigned int previous_depth =
    atomic_fetch_add_explicit(&s_sensitive_operation_depth, 1, memory_order_seq_cst);
  MEMFAULT_ASSERT(previous_depth != UINT_MAX);
}

void memfault_port_coredump_sensitive_operation_end(void) {
  const unsigned int previous_depth =
    atomic_fetch_sub_explicit(&s_sensitive_operation_depth, 1, memory_order_seq_cst);
  MEMFAULT_ASSERT(previous_depth > 0);
}

static bool sensitive_operation_active(void) {
  return atomic_load_explicit(&s_sensitive_operation_depth, memory_order_seq_cst) > 0;
}

static uint32_t sanitize_fault_pc(uint32_t pc) {
  const uintptr_t address = (uintptr_t)(pc & ~1u);
  if (address < (uintptr_t)__privileged_functions_start__ ||
      address > (uintptr_t)__unprivileged_flash_end__) {
    return 0;
  }
  return pc;
}

// This hook runs in fault/handler mode, where the scheduler cannot release an RTOS lock held by
// the interrupted task. Keep this path limited to lock-free atomics, direct RAM access, and the
// non-returning hardware reset. In particular, do not log, acquire an RTOS primitive, or touch the
// filesystem here: those operations can block forever and leave the watchdog to reset the device.
void memfault_platform_fault_handler(const sMfltRegState* regs, eMemfaultRebootReason reason) {
  if (!sensitive_operation_active()) {
    return;
  }

  // Memfault calls this hook before it serializes the register block. A fault during a sensitive
  // operation must not return to that path: general-purpose registers can contain key material,
  // and LR can be used as a scratch register. Preserve only the fault reason and instruction
  // address in reboot tracking so the crash remains visible after reboot.
  uint32_t pc = (regs != NULL && regs->exception_frame != NULL)
                  ? sanitize_fault_pc(regs->exception_frame->pc)
                  : 0;
  eMemfaultRebootReason tracked_reason = reason;

  // Memfault can record a more specific reason and callsite before trapping into this hook. For
  // example, its assert path records an Assert before the UDF instruction enters here as a
  // UsageFault. Preserve that first record when present, then replace it so its LR cannot survive.
  sMfltResetReasonInfo previous_info = {0};
  if (memfault_reboot_tracking_read_reset_info(&previous_info)) {
    tracked_reason = previous_info.reason;
    const uint32_t previous_pc = sanitize_fault_pc(previous_info.pc);
    if (previous_pc != 0) {
      pc = previous_pc;
    }
  }

  const sMfltRebootTrackingRegInfo info = {
    .pc = pc,
    .lr = 0,
  };
  memfault_reboot_tracking_clear_reset_info();
  memfault_reboot_tracking_mark_reset_imminent(tracked_reason, &info);

  // Do not log or touch the filesystem from fault context. Clearing the RAM buffer ensures no
  // stale or partially assembled coredump can be persisted by another reset path.
  memfault_platform_coredump_storage_clear();
  mcu_reset_with_reason(MCU_RESET_FAULT_COREDUMP_SKIPPED);
}

void memfault_platform_get_device_info(sMemfaultDeviceInfo* info) {
  // IMPORTANT: All strings returned in info must be constant
  // or static as they will be used _after_ the function returns
  const sysinfo_t* const sysinfo = sysinfo_get();
  info->device_serial = sysinfo->serial;
  info->hardware_version = sysinfo->hardware_revision;
  info->software_type = sysinfo->software_type;
  info->software_version = sysinfo->version_string;
}

// Last function called after a coredump is saved. Should perform
// any final cleanup and then reset the device
void memfault_platform_reboot(void) {
  // Only persist the coredump to the filesystem if no other context is
  // mid-filesystem-operation.  Writing into LittleFS while its in-memory
  // state is partially updated (another task holds fs_lock) can corrupt
  // the filesystem metadata on flash.
  if (!fs_is_busy()) {
    telemetry_coredump_save();
    mcu_reset_with_reason(MCU_RESET_FAULT);
  } else {
    // Coredump lost — filesystem was busy and we can't safely write.
    // Use a distinct reset reason so this shows up in Memfault telemetry.
    mcu_reset_with_reason(MCU_RESET_FAULT_COREDUMP_SKIPPED);
  }

  FATAL()

  // Necessary to sate 'noreturn'.
  while (1) {
  }
}

bool memfault_platform_time_get_current(sMemfaultCurrentTime* time) {
  // If the device tracks real time, update 'unix_timestamp_secs' with seconds since epoch
  // This will cause events logged by the SDK to be timestamped on the device rather than when they
  // arrive on the server
  *time = (sMemfaultCurrentTime){
    .type = kMemfaultCurrentTimeType_Unknown,
    .info = {.unix_timestamp_secs = 0},
  };

  // If device does not track time, return false, else return true if time is valid
  return false;
}

size_t memfault_platform_sanitize_address_range(void* start_addr, size_t desired_size) {
  const uint32_t ram_start = (uint32_t)ram_addr;
  const uint32_t ram_end = ram_start + ((uint32_t)ram_size);
  if ((uint32_t)start_addr >= ram_start && (uint32_t)start_addr < ram_end) {
    return MEMFAULT_MIN(desired_size, ram_end - (uint32_t)start_addr);
  }
  return 0;
}

void memfault_port_drain_only_events(void) {
  memfault_packetizer_set_active_sources(kMfltDataSourceMask_Event);
}

void memfault_port_drain_all(void) {
  memfault_packetizer_set_active_sources(kMfltDataSourceMask_All);
}

// This function _must_ be called by your main() routine prior
// to starting an RTOS or baremetal loop.
int memfault_platform_boot(void) {
  memfault_freertos_port_boot();

  memfault_platform_reboot_tracking_boot();

  // initialize the event storage buffer
  const sMemfaultEventStorageImpl* evt_storage =
    memfault_events_storage_boot(telemetry_event_storage_get(), TELEMETRY_EVENT_STORAGE_SIZE);

  // configure trace events to store into the buffer
  memfault_trace_event_boot(evt_storage);

  // record the current reboot reason
  memfault_reboot_tracking_collect_reset_info(evt_storage);

  // configure the metrics component to store into the buffer
  sMemfaultMetricBootInfo boot_info = {
    .unexpected_reboot_count = memfault_reboot_tracking_get_crash_count(),
  };
  memfault_metrics_boot(evt_storage, &boot_info);

  memfault_log_boot(telemetry_log_storage_get(), TELEMETRY_LOG_STORAGE_SIZE);

  platform_coredump_init();

  // Memfault has as predefined enum for reset reasons that can't be changed.
  // However, we have some custom reset reasons we'd like to track. To work
  // around this, we grab our custom reset reason and record it as a trace event.
  //
  // NOTE: This must come after the rest of the memfault sdk is initialized.
  const mcu_reset_reason_t boot_reset_reason = mcu_reset_get_reason();
  LOGI("Boot reset reason: %u", (unsigned)boot_reset_reason);
  MEMFAULT_TRACE_EVENT_WITH_STATUS(reset_reason, boot_reset_reason);

#if MEMFAULT_DUMP_BUILD_AND_DEVICE_INFO
  memfault_build_info_dump();
  memfault_device_info_dump();
#endif

  return 0;
}

void memfault_platform_reboot_tracking_boot(void) {
  sResetBootupInfo reset_info = {0};
  memfault_reboot_reason_get(&reset_info);
  memfault_reboot_tracking_boot(s_reboot_tracking, &reset_info);
}

void memfault_reboot_reason_get(sResetBootupInfo* info) {
  const uint32_t reset_cause = mcu_reset_rmu_cause_get();

  MEMFAULT_PRINT_RESET_INFO("Reset Reason, RSTCAUSE=0x%" PRIx32, reset_cause);
  MEMFAULT_PRINT_RESET_INFO("Reset Causes: ");

  *info = (sResetBootupInfo){
    .reset_reason_reg = reset_cause,
    .reset_reason = memfault_translate_rmu_cause_to_memfault_enum(reset_cause),
  };

  mcu_reset_rmu_clear();
}

const sMfltCoredumpRegion* memfault_platform_coredump_get_regions(
#if 0
// Regions are groups of data, like 'active task stack', '.bss', 'non-active task tcb', etc.
// They vary in size.
#define COREDUMP_REGIONS_LIMIT (4 + (MEMFAULT_PLATFORM_MAX_TASK_REGIONS))
#endif

  const sCoredumpCrashInfo* crash_info, size_t* num_regions) {
  const size_t active_stack_size_to_collect = MEMFAULT_PLATFORM_ACTIVE_STACK_SIZE_TO_COLLECT;
  static sMfltCoredumpRegion s_coredump_regions[1];

  int region_idx = 0;

  // 1) Capture the active stack.
  s_coredump_regions[0] = MEMFAULT_COREDUMP_MEMORY_REGION_INIT(
    crash_info->stack_address, memfault_platform_sanitize_address_range(
                                 crash_info->stack_address, active_stack_size_to_collect));
  region_idx++;

  // We used to capture the below regions, but decided to make coredumps smaller because NFC is
  // slow. This can be re-enabled IF TELEMETRY_COREDUMP_SIZE is increased.
#if 0
  // 2) Capture RAM within the memfault_capture bounds.
  extern uint32_t __memfault_capture_freertos_start;
  extern uint32_t __memfault_capture_freertos_end;
  const size_t memfault_freertos_region_size =
    (uint32_t)&__memfault_capture_freertos_end - (uint32_t)&__memfault_capture_freertos_start;
  printf("region: %d\n", memfault_freertos_region_size);
  s_coredump_regions[region_idx] = MEMFAULT_COREDUMP_MEMORY_REGION_INIT(
    &__memfault_capture_freertos_start, memfault_freertos_region_size);
  region_idx++;

  extern uint32_t __memfault_capture_data_start;
  extern uint32_t __memfault_capture_data_end;
  const size_t memfault_capture_data_region_size =
    (uint32_t)&__memfault_capture_data_end - (uint32_t)&__memfault_capture_data_start;
  s_coredump_regions[region_idx] = MEMFAULT_COREDUMP_MEMORY_REGION_INIT(
    &__memfault_capture_data_start, memfault_capture_data_region_size);
  region_idx++;

  extern uint32_t __memfault_capture_bss_start;
  extern uint32_t __memfault_capture_bss_end;
  const size_t memfault_capture_bss_region_size =
    (uint32_t)&__memfault_capture_bss_end - (uint32_t)&__memfault_capture_bss_start;
  s_coredump_regions[region_idx] = MEMFAULT_COREDUMP_MEMORY_REGION_INIT(
    &__memfault_capture_bss_start, memfault_capture_bss_region_size);
  region_idx++;

  // 3) Capture non-active tasks.
  region_idx += memfault_freertos_get_task_regions(
    &s_coredump_regions[region_idx], MEMFAULT_ARRAY_SIZE(s_coredump_regions) - region_idx);
#endif
  *num_regions = region_idx;

  return &s_coredump_regions[0];
}

static void platform_coredump_init(void) {
  sMfltCoredumpStorageInfo storage_info = {0};
  memfault_platform_coredump_storage_get_info(&storage_info);
  const size_t size_needed = memfault_coredump_storage_compute_size_required();
  if (size_needed > storage_info.size) {
    MEMFAULT_LOG_ERROR("Coredump storage too small. Got %d B, need %d B", storage_info.size,
                       size_needed);
  }
  MEMFAULT_ASSERT(size_needed <= storage_info.size);
}
